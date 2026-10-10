package de.hbt.salat.jira.service;

import static de.hbt.salat.common.exception.ErrorCode.AA_NEEDS_MANAGER;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_OAUTH_NOT_SELECTED;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_OAUTH_SITE_NOT_ACCESSIBLE;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_OAUTH_STATE_INVALID;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_OAUTH_TOKEN_REQUEST_FAILED;
import static de.hbt.salat.jira.service.JiraCredentialStore.WRITE_SCOPE;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.util.DateTimeUtils;
import de.hbt.salat.jira.domain.JiraApiFlavor;
import de.hbt.salat.jira.domain.JiraAuthMethod;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.jira.oauth.JiraOAuthAuthorization;
import de.hbt.salat.jira.oauth.JiraOAuthService;
import de.hbt.salat.jira.persistence.JiraReplicationConfigRepository;
import de.hbt.salat.secret.domain.OAuthConnection;

/**
 * Connects a replication to an Atlassian account via OAuth 2.0 (3LO) (#1417, → ADR-0038): start,
 * callback, disconnect. The flow in between — {@code state}, PKCE, the code against tokens — is
 * {@link JiraOAuthService}'s; here is what only the replication knows: who may connect, which scopes
 * it needs, and which site its base URL names.
 *
 * <p>Managers only, like everything around the replications; only who may edit a replication may
 * connect it, connect it again or disconnect it.
 *
 * <p>Atlassian offers no endpoint to revoke a grant. Disconnecting deletes the tokens in SALAT; the
 * access of the app is withdrawn in the Atlassian account, which the form says.
 */
@Slf4j
@Service
@Authorized(requiresManager = true)
public class JiraReplicationOAuthService {

  /** What the replication reads: issues and fields, and the name of the account it acts as. */
  static final List<String> READ_SCOPES = List.of("read:jira-work", "read:me", "offline_access");

  private static final String OWNER_PREFIX = "jira-replication:";

  private final JiraReplicationConfigRepository configRepository;
  private final JiraCredentialStore credentialStore;
  private final JiraOAuthService oauthService;
  private final AtlassianAccountClient accountClient;
  private final AuthorizedUser authorizedUser;
  private final TransactionTemplate transaction;

  JiraReplicationOAuthService(JiraReplicationConfigRepository configRepository, JiraCredentialStore credentialStore,
                              JiraOAuthService oauthService, AtlassianAccountClient accountClient,
                              AuthorizedUser authorizedUser, PlatformTransactionManager transactionManager) {
    this.configRepository = configRepository;
    this.credentialStore = credentialStore;
    this.oauthService = oauthService;
    this.accountClient = accountClient;
    this.authorizedUser = authorizedUser;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  /**
   * Starts connecting a stored replication: where the browser goes, and the cookie that goes with
   * it. Writing worklogs needs a scope of its own, asked for only when the replication writes them;
   * switched on later, the replication has to be connected again.
   *
   * @throws BusinessRuleException {@code JI-0047} when the stored replication does not sign in with
   *     OAuth — an unsaved change of the form does not count; {@code JI-0050} without a registration
   */
  @Transactional(readOnly = true)
  public JiraOAuthAuthorization startConnection(long replicationId) {
    checkManager();
    var config = load(replicationId);
    if (config.getAuthMethod() != JiraAuthMethod.OAUTH || config.getApiFlavor() != JiraApiFlavor.CLOUD) {
      throw new BusinessRuleException(JI_REPLICATION_OAUTH_NOT_SELECTED);
    }
    var scopes = new ArrayList<>(READ_SCOPES);
    if (Boolean.TRUE.equals(config.getWorklogSyncEnabled())) {
      scopes.add(WRITE_SCOPE);
    }
    var parameters = new LinkedHashMap<String, String>();
    parameters.put("audience", "api.atlassian.com");
    // Atlassian asks for consent every time anyway; named, so it stays that way.
    parameters.put("prompt", "consent");
    log.info("Connecting JIRA replication {} to an Atlassian account started by {}, scopes {}", replicationId,
        authorizedUser.getLoginSign(), scopes);
    return oauthService.authorize(OWNER_PREFIX + replicationId, scopes, parameters);
  }

  /**
   * The replication a callback is for, from the cookie of the attempt.
   *
   * @throws BusinessRuleException {@code JI-0051} when the callback belongs to no attempt of this
   *     person in this browser, or to something other than a replication
   */
  public long replicationOf(String cookieValue, String state) {
    checkManager();
    var owner = oauthService.ownerOf(cookieValue, state);
    if (owner == null || !owner.startsWith(OWNER_PREFIX)) {
      throw new BusinessRuleException(JI_REPLICATION_OAUTH_STATE_INVALID);
    }
    try {
      return Long.parseLong(owner.substring(OWNER_PREFIX.length()));
    } catch (NumberFormatException ex) {
      throw new BusinessRuleException(JI_REPLICATION_OAUTH_STATE_INVALID);
    }
  }

  /**
   * Finishes connecting: the code against tokens, the site of the base URL among the sites the
   * account reaches, the account's name, and then — only then — the tokens stored.
   *
   * <p>Outside a transaction: Atlassian is asked three times, and a database connection must not
   * wait for that. The tokens are written in a short transaction of their own.
   *
   * @throws BusinessRuleException {@code JI-0045} when the account does not reach the site of the base
   *     URL — nothing is stored then; the codes of {@link JiraOAuthService#complete} for the callback itself
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void completeConnection(long replicationId, String cookieValue, String state, String code, String error) {
    checkManager();
    var grant = oauthService.complete(cookieValue, state, code, error);
    var config = transaction.execute(tx -> requireOAuth(load(replicationId)));
    AtlassianAccountClient.Site site;
    AtlassianAccountClient.Account account;
    try {
      site = accountClient.accessibleSites(grant.accessToken()).stream()
          .filter(AtlassianAccountClient.Site::isJira)
          .filter(candidate -> JiraCredentialStore.isSameSite(config.getBaseUrl(), candidate.url()))
          .findFirst()
          .orElse(null);
      if (site == null) {
        log.info("Connecting JIRA replication {} by {} refused: the Atlassian account does not reach {}",
            replicationId, authorizedUser.getLoginSign(), config.getBaseUrl());
        throw new BusinessRuleException(JI_REPLICATION_OAUTH_SITE_NOT_ACCESSIBLE, config.getBaseUrl());
      }
      account = accountClient.me(grant.accessToken());
    } catch (RestClientException ex) {
      log.warn("Connecting JIRA replication {}: Atlassian could not be asked for sites and account: {}",
          replicationId, ex.getClass().getSimpleName());
      throw new BusinessRuleException(JI_REPLICATION_OAUTH_TOKEN_REQUEST_FAILED, "accessible_resources");
    }
    var connection = new OAuthConnection(JiraOAuthService.PROVIDER, account.accountId(), account.displayName(), site.id(),
        site.url(), grant.scopes(), authorizedUser.getLoginSign(), DateTimeUtils.now());
    var tokens = grant.toTokens(connection);
    transaction.executeWithoutResult(tx -> {
      var stored = requireOAuth(load(replicationId));
      credentialStore.connect(stored, tokens);
      configRepository.save(stored);
    });
    log.info("JIRA replication {} connected to the Atlassian account {} on {} by {}, scopes {}", replicationId,
        account.accountId(), site.url(), authorizedUser.getLoginSign(), grant.scopes());
  }

  /** The cookie of the attempt removed; the callback sends it whatever came of it. */
  public ResponseCookie clearedCookie() {
    return oauthService.clearedCookie();
  }

  /** Deletes the tokens and forgets them (ADR-0038 §6). The replication stays, and does not run until connected. */
  @Transactional
  public void disconnect(long replicationId) {
    checkManager();
    var config = load(replicationId);
    credentialStore.disconnect(config);
    configRepository.save(config);
    log.info("JIRA replication {} disconnected from Atlassian by {}", replicationId, authorizedUser.getLoginSign());
  }

  /** The replication was switched to another method while the person was at Atlassian. */
  private static JiraReplicationConfig requireOAuth(JiraReplicationConfig config) {
    if (config.getAuthMethod() != JiraAuthMethod.OAUTH) {
      throw new BusinessRuleException(JI_REPLICATION_OAUTH_NOT_SELECTED);
    }
    return config;
  }

  private JiraReplicationConfig load(long id) {
    return configRepository.findById(id).orElseThrow(() -> new InvalidDataException(JI_REPLICATION_NOT_FOUND));
  }

  private void checkManager() {
    if (!authorizedUser.isManager()) {
      throw new AuthorizationException(AA_NEEDS_MANAGER);
    }
  }
}
