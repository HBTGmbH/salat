package org.tb.jira.domain;

/**
 * The JIRA REST API a replication config talks to. The two are not interchangeable: Cloud removed
 * {@code /rest/api/latest/search} and pages with an opaque token instead of {@code startAt}.
 */
public enum JiraApiFlavor {

  /** JIRA Server / Data Center: {@code GET /rest/api/latest/search}, paged via {@code startAt}. */
  SERVER,

  /** JIRA Cloud: {@code POST /rest/api/3/search/jql}, paged via {@code nextPageToken}. */
  CLOUD
}
