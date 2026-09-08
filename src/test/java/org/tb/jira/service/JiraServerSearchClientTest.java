package org.tb.jira.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.tb.jira.domain.JiraApiFlavor.SERVER;

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

class JiraServerSearchClientTest {

  private static final String SEARCH_URL = "https://mock-jira.com/rest/api/latest/search";

  private MockRestServiceServer jira;
  private JiraServerSearchClient client;

  @BeforeEach
  void setUp() {
    var builder = RestClient.builder();
    jira = MockRestServiceServer.bindTo(builder).build();
    client = new JiraServerSearchClient(builder);
  }

  @Test
  void testFirstRequestCarriesCredentialsAndQuery() {
    jira.expect(requestTo(startsWith(SEARCH_URL + "?")))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuth("mockUser", "mockPassword")))
        // queryParam matches against the raw query string, hence the encoded form
        .andExpect(queryParam("jql", "project%20%3D%20MOCK"))
        .andExpect(queryParam("startAt", "0"))
        .andExpect(queryParam("maxResults", "2"))
        .andExpect(queryParam("fields", "summary,updated"))
        .andRespond(withSuccess(page(1, "MOCK-1"), MediaType.APPLICATION_JSON));

    assertEquals(List.of("MOCK-1"), keys(client.search(request())));
    jira.verify();
  }

  @Test
  void testSecondPageIsRequestedWithAnAdvancedStartAt() {
    jira.expect(requestTo(startsWith(SEARCH_URL + "?")))
        .andExpect(queryParam("startAt", "0"))
        .andRespond(withSuccess(page(3, "MOCK-1", "MOCK-2"), MediaType.APPLICATION_JSON));
    jira.expect(requestTo(startsWith(SEARCH_URL + "?")))
        .andExpect(queryParam("startAt", "2"))
        .andRespond(withSuccess(page(3, "MOCK-3"), MediaType.APPLICATION_JSON));

    assertEquals(List.of("MOCK-1", "MOCK-2", "MOCK-3"), keys(client.search(request())));
    jira.verify();
  }

  @Test
  void testIterationEndsOnceTotalIsReached() {
    jira.expect(requestTo(startsWith(SEARCH_URL + "?")))
        .andRespond(withSuccess(page(1, "MOCK-1"), MediaType.APPLICATION_JSON));

    // a second request would fail as unexpected - only one is registered
    assertEquals(List.of("MOCK-1"), keys(client.search(request())));
    jira.verify();
  }

  @Test
  void testEmptyResultEndsTheIteration() {
    jira.expect(requestTo(startsWith(SEARCH_URL + "?")))
        .andRespond(withSuccess(page(7), MediaType.APPLICATION_JSON));

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
  void testFlavorIsServer() {
    assertEquals(SERVER, client.flavor());
  }

  private static JiraSearchRequest request() {
    return new JiraSearchRequest("https://mock-jira.com", "mockUser", "mockPassword",
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
  private static String page(int total, String... issueKeys) {
    var issues = new ArrayList<String>();
    for (int i = 0; i < issueKeys.length; i++) {
      issues.add("""
          {"id": "%d", "key": "%s", "fields": {"summary": "Mock Summary"}}"""
          .formatted(1000 + i, issueKeys[i]));
    }
    return """
        {"expand": "schema,names", "startAt": 0, "maxResults": 2, "total": %d, "issues": [%s]}"""
        .formatted(total, String.join(",", issues));
  }

  private static String basicAuth(String username, String password) {
    return "Basic " + Base64.getEncoder()
        .encodeToString((username + ":" + password).getBytes(UTF_8));
  }
}
