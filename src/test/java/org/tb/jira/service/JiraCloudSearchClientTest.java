package org.tb.jira.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.tb.jira.domain.JiraApiFlavor.CLOUD;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class JiraCloudSearchClientTest {

  private static final String SEARCH_URL = "https://mock.atlassian.net/rest/api/3/search/jql";
  private static final String EMAIL = "service-account@example.com";
  private static final String API_TOKEN = "ATATT3xFfGF0-mock-token";

  private MockRestServiceServer jira;
  private JiraCloudSearchClient client;

  @BeforeEach
  void setUp() {
    var builder = RestClient.builder();
    jira = MockRestServiceServer.bindTo(builder).build();
    client = new JiraCloudSearchClient(builder);
  }

  @Test
  void testFirstRequestCarriesCredentialsAndQueryButNoPageToken() {
    jira.expect(requestTo(SEARCH_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuth(EMAIL, API_TOKEN)))
        .andExpect(jsonPath("$.jql").value("project = MOCK"))
        .andExpect(jsonPath("$.maxResults").value(2))
        .andExpect(jsonPath("$.fields[0]").value("summary"))
        .andExpect(jsonPath("$.fields[1]").value("updated"))
        // sending the token as null is rejected as invalid, so the key must be absent
        .andExpect(jsonPath("$.nextPageToken").doesNotExist())
        .andRespond(withSuccess(page(null, "MOCK-1"), MediaType.APPLICATION_JSON));

    assertEquals(List.of("MOCK-1"), keys(client.search(request())));
    jira.verify();
  }

  @Test
  void testSecondPageIsRequestedWithTheTokenOfTheFirst() {
    jira.expect(requestTo(SEARCH_URL))
        .andExpect(jsonPath("$.nextPageToken").doesNotExist())
        .andRespond(withSuccess(page("token-page-2", "MOCK-1", "MOCK-2"), MediaType.APPLICATION_JSON));
    jira.expect(requestTo(SEARCH_URL))
        .andExpect(jsonPath("$.nextPageToken").value("token-page-2"))
        .andRespond(withSuccess(page(null, "MOCK-3"), MediaType.APPLICATION_JSON));

    assertEquals(List.of("MOCK-1", "MOCK-2", "MOCK-3"), keys(client.search(request())));
    jira.verify();
  }

  @Test
  void testMissingPageTokenEndsTheIterationWithoutAFurtherRequest() {
    jira.expect(requestTo(SEARCH_URL))
        .andRespond(withSuccess(page(null, "MOCK-1"), MediaType.APPLICATION_JSON));

    // a second request would fail as unexpected - only one is registered
    assertEquals(List.of("MOCK-1"), keys(client.search(request())));
    jira.verify();
  }

  @Test
  void testRepeatedPageTokenEndsTheIteration() {
    jira.expect(requestTo(SEARCH_URL))
        .andRespond(withSuccess(page("stuck", "MOCK-1"), MediaType.APPLICATION_JSON));
    jira.expect(requestTo(SEARCH_URL))
        .andExpect(jsonPath("$.nextPageToken").value("stuck"))
        // JIRA hands back the very token it was given - following it would serve page 1 forever
        .andRespond(withSuccess(page("stuck", "MOCK-1"), MediaType.APPLICATION_JSON));

    assertEquals(List.of("MOCK-1", "MOCK-1"), keys(client.search(request())));
    jira.verify();
  }

  @Test
  void testEmptyResultEndsTheIteration() {
    jira.expect(requestTo(SEARCH_URL))
        .andRespond(withSuccess(page("token-page-2"), MediaType.APPLICATION_JSON));

    assertEquals(List.of(), keys(client.search(request())));
    jira.verify();
  }

  @Test
  void testSearchDoesNotCallJiraBeforeTheIteratorIsAdvanced() {
    // no expectation is registered, so any request would fail as unexpected
    client.search(request());

    jira.verify();
  }

  @Test
  void testFlavorIsCloud() {
    assertEquals(CLOUD, client.flavor());
  }

  private static JiraSearchRequest request() {
    return new JiraSearchRequest("https://mock.atlassian.net", EMAIL, API_TOKEN,
        "project = MOCK", List.of("summary", "updated"), 2);
  }

  private static List<String> keys(Iterator<JiraIssue> issues) {
    var keys = new ArrayList<String>();
    while (issues.hasNext()) {
      keys.add(issues.next().getKey());
    }
    return keys;
  }

  /** A search response, with an unknown top-level field as real JIRA responses have them. */
  private static String page(String nextPageToken, String... issueKeys) {
    var issues = new ArrayList<String>();
    for (int i = 0; i < issueKeys.length; i++) {
      issues.add("""
          {"id": "%d", "key": "%s", "fields": {"summary": "Mock Summary"}}"""
          .formatted(1000 + i, issueKeys[i]));
    }
    var token = nextPageToken == null ? "" : "\"nextPageToken\": \"%s\", ".formatted(nextPageToken);
    return "{%s\"isLast\": false, \"issues\": [%s]}".formatted(token, String.join(",", issues));
  }

  private static String basicAuth(String username, String password) {
    return "Basic " + Base64.getEncoder()
        .encodeToString((username + ":" + password).getBytes(UTF_8));
  }
}
