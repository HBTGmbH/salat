package org.tb.jira.service;

import static org.tb.jira.domain.JiraApiFlavor.SERVER;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import org.tb.jira.domain.JiraApiFlavor;

/**
 * Searches issues in JIRA Server / Data Center via {@code GET /rest/api/latest/search}, paging with
 * {@code startAt} until the reported {@code total} is reached.
 */
@Slf4j
@Component
public class JiraServerSearchClient extends AbstractJiraSearchClient {

  private static final String SEARCH_PATH = "rest/api/latest/search";

  public JiraServerSearchClient() {
    this(RestClient.builder());
  }

  /** For tests, which bind a {@code MockRestServiceServer} to the builder they pass in. */
  JiraServerSearchClient(RestClient.Builder restClientBuilder) {
    super(restClientBuilder);
  }

  @Override
  public JiraApiFlavor flavor() {
    return SERVER;
  }

  @Override
  public Iterator<JiraIssue> search(JiraSearchRequest request) {
    return new StartAtIterator(request);
  }

  private class StartAtIterator extends PagedJiraIssueIterator {

    private final JiraSearchRequest request;
    private final RestClient client;
    private int startAt;

    private StartAtIterator(JiraSearchRequest request) {
      this.request = request;
      this.client = clientFor(request.username(), request.password());
    }

    @Override
    protected List<JiraIssue> fetchNextPage() {
      URI uri = UriComponentsBuilder.fromUriString(endpointUrl(request.baseUrl(), SEARCH_PATH))
          .queryParam("jql", request.jql())
          .queryParam("startAt", startAt)
          .queryParam("maxResults", request.pageSize())
          .queryParamIfPresent("fields", fieldList())
          .build()
          .encode()
          .toUri();

      log.info("JIRA server search: baseUrl={}, startAt={}, maxResults={}, jqlSnippet={}, fields={}",
          request.baseUrl(), startAt, request.pageSize(), abbreviate(request.jql(), 120),
          String.join(",", request.fields()));

      JiraServerSearchResponse response;
      try {
        response = client.get().uri(uri).retrieve().body(JiraServerSearchResponse.class);
      } catch (RestClientException ex) {
        log.error("JIRA server search failed: baseUrl={}, startAt={}, maxResults={}, jqlSnippet={}",
            request.baseUrl(), startAt, request.pageSize(), abbreviate(request.jql(), 120), ex);
        throw ex;
      }

      if (response == null || response.getIssues() == null || response.getIssues().isEmpty()) {
        noMorePages();
        return List.of();
      }

      var issues = response.getIssues();
      startAt += issues.size();
      log.info("JIRA server search result: issues.size={}, startAt={}, total={}",
          issues.size(), startAt, response.getTotal());
      if (startAt >= response.getTotal()) {
        noMorePages();
      }
      return issues;
    }

    private Optional<String> fieldList() {
      return request.fields().isEmpty()
          ? Optional.empty()
          : Optional.of(String.join(",", request.fields()));
    }
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class JiraServerSearchResponse {

    private int startAt;
    private int maxResults;
    private int total;
    private List<JiraIssue> issues;
  }
}
