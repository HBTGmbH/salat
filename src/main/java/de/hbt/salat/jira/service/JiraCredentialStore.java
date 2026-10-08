package de.hbt.salat.jira.service;

import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_SECRET_MISSING;
import static de.hbt.salat.common.exception.ErrorCode.SE_NO_KEY;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.jira.domain.JiraAuthMethod;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
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
 * account, and useless without the token. A Personal Access Token is a secret of its own type.
 */
@Component
@RequiredArgsConstructor
class JiraCredentialStore {

  private final SecretService secretService;

  /** Whether credentials can be stored — the environment provides a key. */
  boolean canStore() {
    return secretService.isAvailable();
  }

  /**
   * @throws BusinessRuleException {@code JI-0042} when no secret is stored or it does not fit the
   *     sign-in method, {@code SE-0001} without a key, {@code SE-0002} when the secret is unreadable
   *     — each says that the credentials have to be entered again, or why they cannot be
   */
  JiraCredentials credentialsOf(JiraReplicationConfig config) {
    if (config.getSecretId() == null) {
      throw new BusinessRuleException(canStore() ? JI_REPLICATION_SECRET_MISSING : SE_NO_KEY);
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
   * What the form shows of the stored credentials: the user name, and whether they can be read at
   * all. A replication whose plain text secret has not been moved yet — the environment has no key —
   * shows the user name it still keeps in plain text.
   */
  StoredCredentials describe(JiraReplicationConfig config) {
    if (config.getSecretId() == null) {
      return new StoredCredentials(config.getLegacyUsername(), false);
    }
    var summary = secretService.getSummary(config.getSecretId());
    var fits = summary.type() == secretTypeOf(config.getAuthMethod());
    return new StoredCredentials(summary.username(), summary.readable() && fits);
  }

  /**
   * Stores new credentials: replaces the secret, or creates it when the replication has none yet.
   * The plain text columns are cleared — what is entered here is the secret from now on.
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
    config.setLegacyUsername(null);
    config.setLegacyPassword(null);
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
    return method == JiraAuthMethod.PERSONAL_ACCESS_TOKEN ? SecretType.TOKEN : SecretType.USERNAME_PASSWORD;
  }

  /**
   * @param username the stored user name, {@code null} when there is none or it cannot be read
   * @param readable whether the stored credentials can be used as they are
   */
  record StoredCredentials(String username, boolean readable) {

  }
}
