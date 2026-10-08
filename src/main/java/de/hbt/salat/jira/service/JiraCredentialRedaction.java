package de.hbt.salat.jira.service;

/**
 * The reason something failed, with the secret the call used taken out of it. A client library that
 * puts the credentials it used into its message would otherwise print them as a toast or into the
 * run history (#1282, #1432).
 */
final class JiraCredentialRedaction {

  private JiraCredentialRedaction() {
  }

  /** @param credentials what the call signed in with, {@code null} when it got no further */
  static String redacted(Throwable ex, JiraCredentials credentials) {
    var message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    var secret = credentials != null ? credentials.secret() : null;
    return secret == null || secret.isBlank() ? message : message.replace(secret, "***");
  }

}
