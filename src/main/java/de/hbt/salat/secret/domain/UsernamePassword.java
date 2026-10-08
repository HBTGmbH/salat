package de.hbt.salat.secret.domain;

/**
 * User name and password, one secret (#1432). On JIRA Cloud the user name is the e-mail address of
 * the account and the password its API token — the one is useless without the other, so they are
 * written together.
 */
public record UsernamePassword(String username, String password) implements SecretValue {

  @Override
  public SecretType type() {
    return SecretType.USERNAME_PASSWORD;
  }

  @Override
  public String toString() {
    return "UsernamePassword[username=" + username + "]";
  }
}
