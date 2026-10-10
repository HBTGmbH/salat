package de.hbt.salat.jira.oauth;

import java.util.Set;

/**
 * The tokens Atlassian handed out for an authorization code (#1417). The access token serves to find
 * out the site and the account right away and is then dropped; the refresh token is stored as a secret
 * of the replication, and every run gets its own access token with it.
 *
 * @param scopes the scopes Atlassian granted, empty when it did not name them
 */
public record JiraOAuthGrant(String accessToken, String refreshToken, Set<String> scopes) {

  public JiraOAuthGrant {
    scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
  }

  @Override
  public String toString() {
    return "JiraOAuthGrant[scopes=" + scopes + "]";
  }
}
