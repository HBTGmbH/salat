package de.hbt.salat.jira.domain;

import lombok.Getter;

/**
 * How a replication signs in at JIRA (#1385).
 *
 * <p>Only {@link JiraApiFlavor#SERVER} offers a choice: JIRA Server / Data Center accepts a Personal
 * Access Token as a bearer token only, while Atlassian Cloud takes its API token as the HTTP Basic
 * password and accepts no bearer PAT at all.
 */
@Getter
public enum JiraAuthMethod {

  /** HTTP Basic with user name and password — on Cloud the account e-mail and the API token. */
  BASIC("main.jira.replication.authmethod.basic"),

  /** {@code Authorization: Bearer <token>} with a Personal Access Token, without a user name. */
  PERSONAL_ACCESS_TOKEN("main.jira.replication.authmethod.pat");

  private final String label;

  JiraAuthMethod(String label) {
    this.label = label;
  }
}
