package de.hbt.salat.jira.service;

/**
 * The reason something failed, with the stored password taken out of it. A client library that puts
 * the credentials it used into its message would otherwise print them as a toast or into the run
 * history (#1282).
 */
final class JiraCredentialRedaction {

  private JiraCredentialRedaction() {
  }

  static String redacted(Throwable ex, String password) {
    var message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    return password == null || password.isBlank() ? message : message.replace(password, "***");
  }

}
