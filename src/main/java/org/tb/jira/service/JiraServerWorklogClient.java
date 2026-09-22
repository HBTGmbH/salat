package org.tb.jira.service;

import static org.tb.jira.domain.JiraApiFlavor.SERVER;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.tb.jira.domain.JiraApiFlavor;

/**
 * Writes worklogs into JIRA Server / Data Center via {@code rest/api/2}, where a comment is plain
 * text.
 */
@Component
public class JiraServerWorklogClient extends AbstractJiraWorklogClient {

  public JiraServerWorklogClient() {
    this(RestClient.builder());
  }

  /** For tests, which bind a {@code MockRestServiceServer} to the builder they pass in. */
  JiraServerWorklogClient(RestClient.Builder restClientBuilder) {
    super(restClientBuilder);
  }

  @Override
  public JiraApiFlavor flavor() {
    return SERVER;
  }

  @Override
  protected String apiPath() {
    return "rest/api/2";
  }

  @Override
  protected Object comment() {
    return WORKLOG_COMMENT;
  }
}
