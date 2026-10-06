package de.hbt.salat.jira.service;

import static de.hbt.salat.jira.domain.JiraAuthMethod.PERSONAL_ACCESS_TOKEN;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import de.hbt.salat.jira.domain.JiraAuthMethod;
import de.hbt.salat.jira.domain.JiraReplicationConfig;

/**
 * How a client signs in at JIRA (#1385): with user name and password as HTTP Basic, or with a
 * Personal Access Token as bearer token.
 *
 * <p>{@link #toString()} leaves the secret out. A record would print every component, and the
 * requests carrying these credentials end up in log lines and exception messages.
 *
 * @param username the user name for {@link JiraAuthMethod#BASIC}, {@code null} for a token
 * @param secret the password, the Cloud API token or the Personal Access Token
 */
public record JiraCredentials(JiraAuthMethod method, String username, String secret) {

  public static JiraCredentials of(JiraReplicationConfig config) {
    return new JiraCredentials(config.getAuthMethod(), config.getUsername(), config.getPassword());
  }

  public static JiraCredentials basic(String username, String password) {
    return new JiraCredentials(JiraAuthMethod.BASIC, username, password);
  }

  public static JiraCredentials personalAccessToken(String token) {
    return new JiraCredentials(PERSONAL_ACCESS_TOKEN, null, token);
  }

  /** The value of the {@code Authorization} header. */
  String authorizationHeader() {
    if (method == PERSONAL_ACCESS_TOKEN) {
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
