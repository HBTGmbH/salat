package org.tb.jira.service;

import static org.tb.jira.domain.JiraApiFlavor.CLOUD;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.tb.jira.domain.JiraApiFlavor;

/**
 * Writes worklogs into JIRA Cloud via {@code rest/api/3}, where a comment is not a string but an
 * Atlassian Document Format document — a plain string is rejected with a 400.
 */
@Component
public class JiraCloudWorklogClient extends AbstractJiraWorklogClient {

  public JiraCloudWorklogClient() {
    this(RestClient.builder());
  }

  /** For tests, which bind a {@code MockRestServiceServer} to the builder they pass in. */
  JiraCloudWorklogClient(RestClient.Builder restClientBuilder) {
    super(restClientBuilder);
  }

  @Override
  public JiraApiFlavor flavor() {
    return CLOUD;
  }

  @Override
  protected String apiPath() {
    return "rest/api/3";
  }

  @Override
  protected Object comment() {
    return Map.of(
        "type", "doc",
        "version", 1,
        "content", List.of(Map.of(
            "type", "paragraph",
            "content", List.of(Map.of(
                "type", "text",
                "text", WORKLOG_COMMENT)))));
  }
}
