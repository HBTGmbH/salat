package de.hbt.salat.jira.domain;

import java.util.Set;
import lombok.Getter;

/**
 * How a replication signs in at JIRA (#1385, #1417).
 *
 * <p>Each flavor offers two: JIRA Server / Data Center HTTP Basic or a Personal Access Token as
 * bearer token, Atlassian Cloud its API token as the HTTP Basic password or an Atlassian account
 * connected via OAuth. Cloud accepts no bearer PAT, Server knows no Atlassian account.
 */
@Getter
public enum JiraAuthMethod {

  /** HTTP Basic with user name and password — on Cloud the account e-mail and the API token. */
  BASIC("main.jira.replication.authmethod.basic", "main.jira.replication.authmethod.apitoken",
      Set.of(JiraApiFlavor.SERVER, JiraApiFlavor.CLOUD)),

  /** {@code Authorization: Bearer <token>} with a Personal Access Token, without a user name. */
  PERSONAL_ACCESS_TOKEN("main.jira.replication.authmethod.pat", "main.jira.replication.authmethod.pat",
      Set.of(JiraApiFlavor.SERVER)),

  /**
   * An Atlassian account connected via OAuth 2.0 (3LO) (#1417): no secret to type, the tokens are
   * renewed by SALAT. Calls go to {@code api.atlassian.com} with the cloud id of the site, not to the
   * base URL.
   */
  OAUTH("main.jira.replication.authmethod.oauth", "main.jira.replication.authmethod.oauth",
      Set.of(JiraApiFlavor.CLOUD));

  private final String label;
  private final String cloudLabel;
  private final Set<JiraApiFlavor> flavors;

  JiraAuthMethod(String label, String cloudLabel, Set<JiraApiFlavor> flavors) {
    this.label = label;
    this.cloudLabel = cloudLabel;
    this.flavors = flavors;
  }

  public boolean isAvailableOn(JiraApiFlavor flavor) {
    return flavors.contains(flavor);
  }

  /** What the method is called on this flavor: on Cloud the Basic password is the API token. */
  public String labelOn(JiraApiFlavor flavor) {
    return flavor == JiraApiFlavor.CLOUD ? cloudLabel : label;
  }

  /** The label on JIRA Server / Data Center, {@code null} where the method is not offered — for the form. */
  public String labelOnServer() {
    return isAvailableOn(JiraApiFlavor.SERVER) ? labelOn(JiraApiFlavor.SERVER) : null;
  }

  /** The label on JIRA Cloud, {@code null} where the method is not offered — for the form. */
  public String labelOnCloud() {
    return isAvailableOn(JiraApiFlavor.CLOUD) ? labelOn(JiraApiFlavor.CLOUD) : null;
  }

  /** Whether a password or token is typed into the form; with OAuth the account is connected instead. */
  public boolean hasTypedSecret() {
    return this != OAUTH;
  }
}
