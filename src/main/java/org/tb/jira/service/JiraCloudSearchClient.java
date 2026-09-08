package org.tb.jira.service;

import static org.tb.jira.domain.JiraApiFlavor.CLOUD;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.tb.jira.domain.JiraApiFlavor;

/**
 * Searches issues in JIRA Cloud via {@code POST /rest/api/3/search/jql}. Cloud removed the
 * {@code /rest/api/latest/search} endpoint the server client uses — it answers {@code 410 Gone} —
 * and pages with an opaque {@code nextPageToken} instead of {@code startAt}. There is no total, so
 * the only end-of-data signal is a missing token.
 *
 * <p>POST rather than GET because the JQL carries the watermark clause and would otherwise have to
 * fit into a URL.
 */
@Slf4j
@Component
public class JiraCloudSearchClient extends AbstractJiraSearchClient {

  private static final String SEARCH_PATH = "rest/api/3/search/jql";

  public JiraCloudSearchClient() {
    this(RestClient.builder());
  }

  /** For tests, which bind a {@code MockRestServiceServer} to the builder they pass in. */
  JiraCloudSearchClient(RestClient.Builder restClientBuilder) {
    super(restClientBuilder);
  }

  @Override
  public JiraApiFlavor flavor() {
    return CLOUD;
  }

  @Override
  public Iterator<JiraIssue> search(JiraSearchRequest request) {
    return new NextPageTokenIterator(request);
  }

  private class NextPageTokenIterator extends PagedJiraIssueIterator {

    private final JiraSearchRequest request;
    private final RestClient client;
    private final String url;
    private String nextPageToken;

    private NextPageTokenIterator(JiraSearchRequest request) {
      this.request = request;
      this.client = clientFor(request.username(), request.password());
      this.url = endpointUrl(request.baseUrl(), SEARCH_PATH);
    }

    @Override
    protected List<JiraIssue> fetchNextPage() {
      // The token must be absent on the first request — sending it as null is rejected as an
      // invalid token (JRACLOUD-94632), which is why JsonInclude drops it rather than writing null.
      var body = new JiraCloudSearchRequestBody(
          request.jql(), request.fields(), request.pageSize(), nextPageToken);

      log.info("JIRA cloud search: baseUrl={}, pageToken={}, maxResults={}, jqlSnippet={}, fields={}",
          request.baseUrl(), nextPageToken, request.pageSize(), abbreviate(request.jql(), 120),
          String.join(",", request.fields()));

      JiraCloudSearchResponse response;
      try {
        response = client.post().uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JiraCloudSearchResponse.class);
      } catch (RestClientException ex) {
        log.error("JIRA cloud search failed: baseUrl={}, pageToken={}, maxResults={}, jqlSnippet={}",
            request.baseUrl(), nextPageToken, request.pageSize(), abbreviate(request.jql(), 120), ex);
        throw ex;
      }

      if (response == null || response.getIssues() == null || response.getIssues().isEmpty()) {
        noMorePages();
        return List.of();
      }

      var token = response.getNextPageToken();
      if (token == null || token.isBlank()) {
        noMorePages();
      } else if (Objects.equals(token, nextPageToken)) {
        // Cloud is known to hand back the token it was given and re-serve the same page. Stopping
        // here costs at most one page; following it would loop forever.
        log.warn("JIRA cloud search returned the page token it was given, stopping: baseUrl={}",
            request.baseUrl());
        noMorePages();
      } else {
        nextPageToken = token;
      }

      log.info("JIRA cloud search result: issues.size={}, nextPageToken={}",
          response.getIssues().size(), token);
      return response.getIssues();
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record JiraCloudSearchRequestBody(
      String jql,
      List<String> fields,
      int maxResults,
      String nextPageToken
  ) {

  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class JiraCloudSearchResponse {

    private String nextPageToken;
    private List<JiraIssue> issues;
  }
}
