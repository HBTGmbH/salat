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
import de.hbt.salat.jira.domain.JiraOAuthConnection;
import de.hbt.salat.jira.domain.JiraOAuthConnectionInfo;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.jira.oauth.JiraOAuthService;
import de.hbt.salat.secret.domain.SecretStatus;
import de.hbt.salat.secret.domain.SecretType;
import de.hbt.salat.secret.domain.SecretValue;
import de.hbt.salat.secret.domain.Token;
import de.hbt.salat.secret.domain.UsernamePassword;
import de.hbt.salat.secret.service.SecretService;

/**
 * The credentials of a replication, kept encrypted in the module {@code secret} (#1432, → ADR-0038).
 * The one place in jira that reads or writes them: every client gets its {@link JiraCredentials}
 * from here, and the replication remembers nothing but the id of its secret.
 *
 * <p>With HTTP Basic the user name is part of the secret — on Cloud it is the e-mail address of the
 * account, and useless without the token. A Personal Access Token is a secret of its own type. With
 * OAuth (#1417) the secret is the refresh token, a token as well; every client gets a fresh access
 * token from {@link JiraOAuthService}, and the account and site stand with the replication.
 */
@Component
@RequiredArgsConstructor
class JiraCredentialStore {

  /** The scope that lets the connection write worklogs; asked for only when they are written. */
  static final String WRITE_SCOPE = "write:jira-work";

  private final SecretService secretService;
  private final JiraOAuthService oauthService;

  /** Whether credentials can be stored — the environment provides a key. */
  boolean canStore() {
    return secretService.isAvailable();
  }

  /** Whether a replication can be connected to an Atlassian account: the app is registered and there is a key. */
  boolean canConnect() {
    return oauthService.isAvailable();
  }

  /**
   * @throws BusinessRuleException {@code JI-0042} when no secret is stored or it does not fit the
   *     sign-in method, {@code SC-0001} without a key, {@code SC-0002} when the secret is unreadable
   *     — each says that the credentials have to be entered again, or why they cannot be. With OAuth
   *     {@code JI-0044} when it is not connected, {@code JI-0049} when the connection has expired and
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
   * A fresh access token, and the API address of the site the connection was made for.
   * The base URL has to name that site still: changed afterwards, the replication would read another
   * site with the permission granted for this one, or fail with a message nobody understands.
   */
  private JiraCredentials oauthCredentialsOf(JiraReplicationConfig config) {
    var connection = config.getOauthConnection();
    if (config.getSecretId() == null || connection == null) {
      throw new BusinessRuleException(canConnect() ? JI_REPLICATION_OAUTH_NOT_CONNECTED : SC_NO_KEY);
    }
    if (!isSameSite(config.getBaseUrl(), connection.siteUrl())) {
      throw new BusinessRuleException(JI_REPLICATION_OAUTH_SITE_CHANGED, connection.siteUrl());
    }
    var accessToken = oauthService.accessToken(config.getSecretId());
    return JiraCredentials.oauth(accessToken, connection.cloudId(), connection.grants(WRITE_SCOPE));
  }

  /**
   * What the form shows of the stored credentials: the user name, and whether they can be read at
   * all. A replication without a secret shows no user name. With OAuth the connection instead, read
   * from the replication; it has to be established again when Atlassian refused to renew the tokens
   * or they cannot be read here — in a copy of the database from another environment.
   */
  StoredCredentials describe(JiraReplicationConfig config) {
    if (config.getSecretId() == null) {
      return new StoredCredentials(null, false, null);
    }
    var summary = secretService.getSummary(config.getSecretId());
    var fits = summary.type() == secretTypeOf(config.getAuthMethod());
    var connection = config.getOauthConnection();
    var info = fits && connection != null
        ? JiraOAuthConnectionInfo.of(connection,
            summary.status() == SecretStatus.REAUTH_REQUIRED || !summary.readable(),
            isSameSite(config.getBaseUrl(), connection.siteUrl()), WRITE_SCOPE)
        : null;
    return new StoredCredentials(summary.username(), summary.readable() && fits, info);
  }

  /**
   * Stores a new connection: the refresh token replaces the secret — a refresh token, password or
   * token alike — or creates it when the replication has none yet (ADR-0038 §6); what the connection
   * is goes to the replication.
   */
  void connect(JiraReplicationConfig config, String refreshToken, JiraOAuthConnection connection) {
    if (config.getSecretId() == null) {
      config.setSecretId(secretService.create(new Token(refreshToken)));
    } else {
      secretService.replace(config.getSecretId(), new Token(refreshToken));
    }
    config.setOauthConnection(connection);
  }

  /** Deletes the secret and forgets it and the connection, in the same transaction (ADR-0038 §6). */
  void disconnect(JiraReplicationConfig config) {
    delete(config);
    config.setSecretId(null);
    config.setOauthConnection(null);
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
      // The refresh token of the connection (#1417); the access token is never stored.
      case OAUTH -> SecretType.TOKEN;
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
