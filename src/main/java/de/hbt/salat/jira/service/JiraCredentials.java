package de.hbt.salat.jira.service;

import static de.hbt.salat.jira.domain.JiraAuthMethod.BASIC;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import de.hbt.salat.jira.domain.JiraAuthMethod;

/**
 * How a client signs in at JIRA (#1385, #1417): with user name and password as HTTP Basic, with a
 * Personal Access Token as bearer token, or with the access token of a connected Atlassian account.
 *
 * <p>Read from the secret store by {@link JiraCredentialStore} (#1432), never from the replication
 * itself. {@link #toString()} leaves the secret out. A record would print every component, and the
 * requests carrying these credentials end up in log lines and exception messages.
 *
 * @param username the user name for {@link JiraAuthMethod#BASIC}, {@code null} for a token
 * @param secret the password, the Cloud API token, the Personal Access Token or the OAuth access token
 * @param apiBaseUrl where the calls go instead of the base URL of the replication, {@code null} for
 *     the base URL itself. With OAuth the site is not called directly but through
 *     {@code api.atlassian.com} with its cloud id (#1417).
 * @param writeGranted whether the account may write worklogs. With OAuth that is a scope the
 *     connection may lack; the other methods have whatever permissions the account has in JIRA.
 */
public record JiraCredentials(JiraAuthMethod method, String username, String secret, String apiBaseUrl,
                              boolean writeGranted) {

  /** Where JIRA Cloud is reached with an OAuth access token; the cloud id of the site follows. */
  static final String ATLASSIAN_API_BASE_URL = "https://api.atlassian.com/ex/jira/";

  public static JiraCredentials basic(String username, String password) {
    return new JiraCredentials(BASIC, username, password, null, true);
  }

  public static JiraCredentials personalAccessToken(String token) {
    return new JiraCredentials(JiraAuthMethod.PERSONAL_ACCESS_TOKEN, null, token, null, true);
  }

  /**
   * @param cloudId the id of the Atlassian site the connection was made for
   * @param writeGranted whether the connection was granted the scope to write worklogs
   */
  public static JiraCredentials oauth(String accessToken, String cloudId, boolean writeGranted) {
    return new JiraCredentials(JiraAuthMethod.OAUTH, null, accessToken, ATLASSIAN_API_BASE_URL + cloudId,
        writeGranted);
  }

  /** The address the clients call: the API address of the connection, else the configured base URL. */
  String baseUrl(String configuredBaseUrl) {
    return apiBaseUrl != null ? apiBaseUrl : configuredBaseUrl;
  }

  /** The value of the {@code Authorization} header. */
  String authorizationHeader() {
    if (method != BASIC) {
      return "Bearer " + secret;
    }
    var pair = username + ":" + secret;
    return "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public String toString() {
    return "JiraCredentials[method=" + method + ", username=" + username + "]";
  }
}
