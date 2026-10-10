package de.hbt.salat.jira.domain;

import java.time.LocalDateTime;

/**
 * The Atlassian account a replication is connected to (#1417), as the form shows it — without a
 * token.
 *
 * @param accountName the name of the connected Atlassian account; the replication acts as it
 * @param siteUrl the site the connection was made for
 * @param connectedBy the sign of the person who connected
 * @param connectedAt when the connection was made
 * @param reauthRequired whether Atlassian refused to renew the tokens — expired or revoked; only
 *     connecting again helps
 * @param siteMatches whether the base URL still names {@link #siteUrl}; when not, the replication
 *     does not run until it is connected again
 * @param writeGranted whether the connection may write worklogs
 */
public record JiraOAuthConnectionInfo(
    String accountName,
    String siteUrl,
    String connectedBy,
    LocalDateTime connectedAt,
    boolean reauthRequired,
    boolean siteMatches,
    boolean writeGranted
) {

  /** @param writeScope the scope that lets the connection write worklogs */
  public static JiraOAuthConnectionInfo of(JiraOAuthConnection connection, boolean reauthRequired, boolean siteMatches,
                                           String writeScope) {
    return new JiraOAuthConnectionInfo(connection.accountName(), connection.siteUrl(), connection.connectedBy(),
        connection.connectedAt(), reauthRequired, siteMatches, connection.grants(writeScope));
  }

  /** The host of the site, as the form names it. */
  public String siteHost() {
    if (siteUrl == null) {
      return null;
    }
    return siteUrl.replaceFirst("^https?://", "").replaceFirst("/+$", "");
  }
}
