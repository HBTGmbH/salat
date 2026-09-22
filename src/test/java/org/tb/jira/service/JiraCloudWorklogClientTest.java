package org.tb.jira.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.tb.jira.domain.JiraApiFlavor.CLOUD;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class JiraCloudWorklogClientTest {

  private static final String WORKLOGS_URL = "https://mock-jira.com/rest/api/3/issue/MOCK-1/worklog";

  private MockRestServiceServer jira;
  private JiraCloudWorklogClient client;

  @BeforeEach
  void setUp() {
    var builder = RestClient.builder();
    jira = MockRestServiceServer.bindTo(builder).build();
    client = new JiraCloudWorklogClient(builder);
  }

  @Test
  void testFlavorIsCloud() {
    assertThat(client.flavor()).isEqualTo(CLOUD);
  }

  @Test
  void testCreateGoesToTheCloudApiVersion() {
    jira.expect(requestTo(startsWith(WORKLOGS_URL + "?")))
        .andExpect(method(HttpMethod.POST))
        .andExpect(queryParam("adjustEstimate", "leave"))
        .andExpect(queryParam("notifyUsers", "false"))
        .andExpect(jsonPath("$.timeSpentSeconds").value(5400))
        .andRespond(withSuccess("""
            {"id": "20202"}""", MediaType.APPLICATION_JSON));

    var worklogId = client.create(target(), new JiraWorklogEntry(LocalDate.of(2026, 1, 15), 90));

    assertThat(worklogId).isEqualTo("20202");
    jira.verify();
  }

  @Test
  void testTheCommentIsAnAdfDocument() {
    // Cloud rejects a plain string here with a 400 — the comment has to be a document.
    jira.expect(requestTo(startsWith(WORKLOGS_URL + "?")))
        .andExpect(jsonPath("$.comment.type").value("doc"))
        .andExpect(jsonPath("$.comment.version").value(1))
        .andExpect(jsonPath("$.comment.content[0].type").value("paragraph"))
        .andExpect(jsonPath("$.comment.content[0].content[0].text").value("Aus SALAT uebertragen"))
        .andRespond(withSuccess("""
            {"id": "20202"}""", MediaType.APPLICATION_JSON));

    client.create(target(), new JiraWorklogEntry(LocalDate.of(2026, 1, 15), 90));

    jira.verify();
  }

  @Test
  void testUpdateAndDeleteAddressTheWorklogById() {
    jira.expect(requestTo(startsWith(WORKLOGS_URL + "/20202?")))
        .andExpect(method(HttpMethod.PUT))
        .andRespond(withSuccess("""
            {"id": "20202"}""", MediaType.APPLICATION_JSON));
    jira.expect(requestTo(startsWith(WORKLOGS_URL + "/20202?")))
        .andExpect(method(HttpMethod.DELETE))
        .andRespond(withStatus(HttpStatus.NO_CONTENT));

    client.update(target(), "20202", new JiraWorklogEntry(LocalDate.of(2026, 1, 15), 120));
    client.delete(target(), "20202");

    jira.verify();
  }

  @Test
  void testAWorklogGoneFromJiraSaysSo() {
    jira.expect(requestTo(startsWith(WORKLOGS_URL + "/20202?")))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThatThrownBy(() -> client.delete(target(), "20202"))
        .isInstanceOf(JiraWorklogNotFoundException.class);
  }

  private static JiraWorklogTarget target() {
    return new JiraWorklogTarget("https://mock-jira.com", "mockUser", "mockPassword", "MOCK-1");
  }
}
