package de.hbt.salat.jira.service;

import static de.hbt.salat.jira.domain.JiraApiFlavor.CLOUD;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import de.hbt.salat.jira.domain.JiraApiFlavor;

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

  /**
   * One paragraph, its lines joined by {@code hardBreak} nodes (#1408). A line break inside a text
   * node is not one in ADF, and a paragraph per line would put a blank line between the people,
   * unlike the comment JIRA Server shows.
   */
  @Override
  protected Object comment(String text) {
    return Map.of(
        "type", "doc",
        "version", 1,
        "content", List.of(Map.of(
            "type", "paragraph",
            "content", lines(text))));
  }

  private static List<Map<String, String>> lines(String text) {
    var nodes = new ArrayList<Map<String, String>>();
    for (var line : text.split(JiraWorklogEntry.LINE_BREAK)) {
      if (!nodes.isEmpty()) {
        nodes.add(Map.of("type", "hardBreak"));
      }
      nodes.add(Map.of("type", "text", "text", line));
    }
    return nodes;
  }
}
