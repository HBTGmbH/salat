package de.hbt.salat.jira.service;

import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_OAUTH_NOT_CONNECTED;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_OAUTH_SITE_CHANGED;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_SECRET_MISSING;
import static de.hbt.salat.common.exception.ErrorCode.SC_NO_KEY;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.jira.domain.JiraAuthMethod;
import de.hbt.salat.jira.domain.JiraOAuthConnectionInfo;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.secret.domain.OAuthTokens;
import de.hbt.salat.secret.domain.SecretStatus;
import de.hbt.salat.secret.domain.SecretType;
import de.hbt.salat.secret.domain.SecretValue;
import de.hbt.salat.secret.domain.Token;
import de.hbt.salat.secret.domain.UsernamePassword;
import de.hbt.salat.secret.service.OAuthService;
import de.hbt.salat.secret.service.SecretService;

/**
 * The credentials of a replication, kept encrypted in the module {@code secret} (#1432, → ADR-0038).
 * The one place in jira that reads or writes them: every client gets its {@link JiraCredentials}
 * from here, and the replication remembers nothing but the id of its secret.
 *
 * <p>With HTTP Basic the user name is part of the secret — on Cloud it is the e-mail address of the
 * account, and useless without the token. A Personal Access Token is a secret of its own type, and so
 * is an OAuth connection (#1417): its tokens are renewed by {@link OAuthService} whenever a client
 * asks for them.
 */
@Component
@RequiredArgsConstructor
class JiraCredentialStore {

  /** The registration in {@code salat.oauth.clients} a replication connects with (#1417). */
  static final String ATLASSIAN = "atlassian";

  /** The scope that lets the connection write worklogs; asked for only when they are written. */
  static final String WRITE_SCOPE = "write:jira-work";

  private final SecretService secretService;
  private final OAuthService oauthService;

  /** Whether credentials can be stored — the environment provides a key. */
  boolean canStore() {
    return secretService.isAvailable();
  }

  /** Whether a replication can be connected to an Atlassian account: the app is registered and there is a key. */
  boolean canConnect() {
    return oauthService.isAvailable(ATLASSIAN);
  }

  /**
   * @throws BusinessRuleException {@code JI-0042} when no secret is stored or it does not fit the
   *     sign-in method, {@code SC-0001} without a key, {@code SC-0002} when the secret is unreadable
   *     — each says that the credentials have to be entered again, or why they cannot be. With OAuth
   *     {@code JI-0044} when it is not connected, {@code SC-0004} when the connection has expired and
   *     {@code JI-0046} when it was made for another site than the base URL names.
   */
  JiraCredentials credentialsOf(JiraReplicationConfig config) {
    if (config.getAuthMethod() == JiraAuthMethod.OAUTH) {
      return oauthCredentialsOf(config);
    }
    if (config.getSecretId() == null) {
      throw new BusinessRuleException(canStore() ? JI_REPLICATION_SECRET_MISSING : SC_NO_KEY);
    }
    var value = secretService.read(config.getSecretId());
    if (value instanceof UsernamePassword usernamePassword && config.getAuthMethod() == JiraAuthMethod.BASIC) {
      return JiraCredentials.basic(usernamePassword.username(), usernamePassword.password());
    }
    if (value instanceof Token token && config.getAuthMethod() == JiraAuthMethod.PERSONAL_ACCESS_TOKEN) {
      return JiraCredentials.personalAccessToken(token.token());
    }
    throw new BusinessRuleException(JI_REPLICATION_SECRET_MISSING);
  }

  /**
   * A valid access token, renewed if need be, and the API address of the site it was granted for.
   * The base URL has to name that site still: changed afterwards, the replication would read another
   * site with the permission granted for this one, or fail with a message nobody understands.
   */
  private JiraCredentials oauthCredentialsOf(JiraReplicationConfig config) {
    if (config.getSecretId() == null) {
      throw new BusinessRuleException(canConnect() ? JI_REPLICATION_OAUTH_NOT_CONNECTED : SC_NO_KEY);
    }
    var tokens = oauthService.currentTokens(config.getSecretId());
    var connection = tokens.connection();
    if (!isSameSite(config.getBaseUrl(), connection.resourceUrl())) {
      throw new BusinessRuleException(JI_REPLICATION_OAUTH_SITE_CHANGED, connection.resourceUrl());
    }
    return JiraCredentials.oauth(tokens.accessToken(), connection.resourceId(), connection.grants(WRITE_SCOPE));
  }

  /**
   * What the form shows of the stored credentials: the user name, and whether they can be read at
   * all. A replication without a secret shows no user name. With OAuth the connection instead.
   */
  StoredCredentials describe(JiraReplicationConfig config) {
    if (config.getSecretId() == null) {
      return new StoredCredentials(null, false, null);
    }
    var summary = secretService.getSummary(config.getSecretId());
    var fits = summary.type() == secretTypeOf(config.getAuthMethod());
    var connection = fits && summary.connection() != null
        ? JiraOAuthConnectionInfo.of(summary.connection(), summary.status() == SecretStatus.REAUTH_REQUIRED,
            isSameSite(config.getBaseUrl(), summary.connection().resourceUrl()), WRITE_SCOPE)
        : null;
    return new StoredCredentials(summary.username(), summary.readable() && fits, connection);
  }

  /**
   * Stores the tokens of a new connection: replaces the secret — tokens, password or token alike —
   * or creates it when the replication has none yet (ADR-0038 §6).
   */
  void connect(JiraReplicationConfig config, OAuthTokens tokens) {
    if (config.getSecretId() == null) {
      config.setSecretId(secretService.create(tokens));
    } else {
      secretService.replace(config.getSecretId(), tokens);
    }
  }

  /** Deletes the secret and forgets it, in the same transaction (ADR-0038 §6). */
  void disconnect(JiraReplicationConfig config) {
    delete(config);
    config.setSecretId(null);
  }

  /** A trailing slash and the case of the host say nothing about which site is meant. */
  static boolean isSameSite(String baseUrl, String siteUrl) {
    return baseUrl != null && siteUrl != null && normalized(baseUrl).equals(normalized(siteUrl));
  }

  private static String normalized(String url) {
    var trimmed = url.trim().toLowerCase(Locale.ROOT);
    return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
  }

  /**
   * Stores new credentials: replaces the secret, or creates it when the replication has none yet.
   *
   * @param username the user name with {@link JiraAuthMethod#BASIC}, ignored with a token
   */
  void store(JiraReplicationConfig config, JiraAuthMethod method, String username, String secret) {
    SecretValue value = method == JiraAuthMethod.PERSONAL_ACCESS_TOKEN
        ? new Token(secret)
        : new UsernamePassword(username, secret);
    if (config.getSecretId() == null) {
      config.setSecretId(secretService.create(value));
    } else {
      secretService.replace(config.getSecretId(), value);
    }
  }

  /**
   * A new user name for the stored password (HTTP Basic). The password has to be readable for that;
   * otherwise it has to be entered again together with the name.
   */
  void changeUsername(JiraReplicationConfig config, String username) {
    var stored = credentialsOf(config);
    secretService.replace(config.getSecretId(), new UsernamePassword(username, stored.secret()));
  }

  /** Deletes the secret with its replication, in the same transaction (ADR-0038 §6). */
  void delete(JiraReplicationConfig config) {
    if (config.getSecretId() != null) {
      secretService.delete(config.getSecretId());
    }
  }

  private static SecretType secretTypeOf(JiraAuthMethod method) {
    return switch (method) {
      case BASIC -> SecretType.USERNAME_PASSWORD;
      case PERSONAL_ACCESS_TOKEN -> SecretType.TOKEN;
      case OAUTH -> SecretType.OAUTH;
    };
  }

  /**
   * @param username the stored user name, {@code null} when there is none or it cannot be read
   * @param readable whether the stored credentials can be used as they are
   * @param connection the OAuth connection, {@code null} for the other methods and when there is none
   */
  record StoredCredentials(String username, boolean readable, JiraOAuthConnectionInfo connection) {

  }
}
