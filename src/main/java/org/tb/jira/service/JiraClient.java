package org.tb.jira.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JiraClient {

  public JiraSearchResult searchIssues(
      String baseUrl, String username, String password,
      String jql, int startAt, int maxResults, List<String> fields
  ) {

    log.info("JIRA search: baseUrl={}, startAt={}, maxResults={}, jqlSnippet={}, fields={}",
        baseUrl, startAt, maxResults,
        abbreviate(jql, 120),
        String.join(",", fields)
    );

    String url = baseUrl;
    if (!url.endsWith("/")) {
      url += "/";
    }
    url += "rest/api/latest/search";

    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("jql", jql);
    params.add("startAt", String.valueOf(startAt));
    params.add("maxResults", String.valueOf(maxResults));
    if (!fields.isEmpty()) {
      params.add("fields", String.join(",", fields));
    }

    String basicAuth = basicAuth(username, password);

    RestClient client = RestClient.builder()
        .baseUrl(url)
        .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuth)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build();

    try {
      var result = client.get().uri(uriBuilder -> uriBuilder.queryParams(params).build())
          .retrieve()
          .body(JiraSearchResult.class);
      log.info(
          "JIRA search result: issues.size={}, startAt={}, maxResults={}, total={}",
          result.getIssues().size(),
          result.getStartAt(),
          result.getMaxResults(),
          result.getTotal()
      );
      return result;
    } catch (RestClientException ex) {
      log.error("JIRA search failed: baseUrl={}, startAt={}, maxResults={}, jqlSnippet={}", baseUrl,
          startAt, maxResults, abbreviate(jql, 120), ex);
      throw ex;
    }
  }

  private static String basicAuth(String username, String password) {
    String token = username + ":" + password;
    String encoded = Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    return "Basic " + encoded;
  }

  private static String abbreviate(@Nullable String s, int max) {
    if (s == null) return null;
    if (s.length() <= max) return s;
    return s.substring(0, max) + "…";
  }

  // --- Minimal DTOs for JIRA search ---
  @Data
  public static class JiraSearchResult {
    private int startAt;
    private int maxResults;
    private int total;
    private List<JiraIssue> issues;
  }

  @Data
  public static class JiraIssue {
    private String id; // numeric string
    private String key;
    private Map<String, Object> fields;
  }

}
