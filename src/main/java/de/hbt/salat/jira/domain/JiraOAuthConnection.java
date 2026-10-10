package de.hbt.salat.jira.domain;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * The Atlassian account a replication is connected to (#1417): what the connection is, apart from its
 * tokens. None of it is secret, so it stands with the replication as JSON in
 * {@code jira_replication_config.oauth_connection}; the tokens are a secret of the module
 * {@code secret}.
 *
 * <p>It stays when Atlassian refuses to renew the tokens, so the form can say which account has to
 * be connected again; disconnecting and switching to another sign-in method remove it.
 *
 * @param accountId the id of the Atlassian account
 * @param accountName its display name; the replication acts as this account
 * @param cloudId the id of the site, the part of the API address that names it
 * @param siteUrl the address of the site, {@code https://example.atlassian.net}
 * @param scopes the scopes Atlassian granted, which may be fewer than were asked for
 * @param connectedBy the sign of the person who connected
 * @param connectedAt when the connection was made
 */
public record JiraOAuthConnection(
    String accountId,
    String accountName,
    String cloudId,
    String siteUrl,
    Set<String> scopes,
    String connectedBy,
    LocalDateTime connectedAt
) {

  public JiraOAuthConnection {
    scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
  }

  public boolean grants(String scope) {
    return scopes.contains(scope);
  }
}
