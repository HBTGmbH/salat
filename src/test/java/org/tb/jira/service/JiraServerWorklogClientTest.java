package org.tb.jira.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;
import static org.tb.jira.domain.JiraApiFlavor.SERVER;

import java.time.LocalDate;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class JiraServerWorklogClientTest {

  private static final String WORKLOGS_URL = "https://mock-jira.com/rest/api/2/issue/MOCK-1/worklog";

  private MockRestServiceServer jira;
  private JiraServerWorklogClient client;

  @BeforeEach
  void setUp() {
    var builder = RestClient.builder();
    jira = MockRestServiceServer.bindTo(builder).build();
    client = new JiraServerWorklogClient(builder);
  }

  @Test
  void testFlavorIsServer() {
    assertThat(client.flavor()).isEqualTo(SERVER);
  }

  @Test
  void testCreateWritesTheSumAndAnswersWithTheWorklogId() {
    jira.expect(requestTo(startsWith(WORKLOGS_URL + "?")))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuth("mockUser", "mockPassword")))
        // Without these two a re-run would eat the remaining estimate of the issue and set off one
        // mail per worklog to everybody watching it.
        .andExpect(queryParam("adjustEstimate", "leave"))
        .andExpect(queryParam("notifyUsers", "false"))
        .andExpect(jsonPath("$.timeSpentSeconds").value(5400))
        .andExpect(jsonPath("$.comment").value("Aus SALAT uebertragen"))
        .andRespond(withSuccess("""
            {"id": "10101", "self": "https://mock-jira.com/rest/api/2/issue/10000/worklog/10101"}""",
            MediaType.APPLICATION_JSON));

    var worklogId = client.create(target(), new JiraWorklogEntry(LocalDate.of(2026, 1, 15), 90));

    assertThat(worklogId).isEqualTo("10101");
    jira.verify();
  }

  @Test
  void testStartedCarriesMiddayAndAnOffsetWithoutAColon() {
    // JIRA rejects the ISO form with a colon in the offset, and the fixed time of day is what makes
    // a second run hit the same day instead of moving the entry.
    jira.expect(requestTo(startsWith(WORKLOGS_URL + "?")))
        .andExpect(jsonPath("$.started").value("2026-01-15T12:00:00.000+0100"))
        .andRespond(withSuccess("""
            {"id": "10101"}""", MediaType.APPLICATION_JSON));

    client.create(target(), new JiraWorklogEntry(LocalDate.of(2026, 1, 15), 90));

    jira.verify();
  }

  @Test
  void testStartedFollowsTheSummerOffset() {
    jira.expect(requestTo(startsWith(WORKLOGS_URL + "?")))
        .andExpect(jsonPath("$.started").value("2026-07-15T12:00:00.000+0200"))
        .andRespond(withSuccess("""
            {"id": "10101"}""", MediaType.APPLICATION_JSON));

    client.create(target(), new JiraWorklogEntry(LocalDate.of(2026, 7, 15), 90));

    jira.verify();
  }

  @Test
  void testAnAnswerWithoutAnIdIsARefusal() {
    // Without the id the worklog could never be recognised as ours again, and the next run would
    // write a second one next to it. Failing here leaves the row unwritten, so it is tried again.
    jira.expect(requestTo(startsWith(WORKLOGS_URL + "?")))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.create(target(), new JiraWorklogEntry(LocalDate.of(2026, 1, 15), 90)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void testUpdateOverwritesTheWorklogOfThatDay() {
    jira.expect(requestTo(startsWith(WORKLOGS_URL + "/10101?")))
        .andExpect(method(HttpMethod.PUT))
        .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuth("mockUser", "mockPassword")))
        .andExpect(queryParam("adjustEstimate", "leave"))
        .andExpect(jsonPath("$.timeSpentSeconds").value(7200))
        .andRespond(withSuccess("""
            {"id": "10101"}""", MediaType.APPLICATION_JSON));

    client.update(target(), "10101", new JiraWorklogEntry(LocalDate.of(2026, 1, 15), 120));

    jira.verify();
  }

  @Test
  void testUpdateOfAWorklogSomebodyDeletedInJiraSaysSo() {
    jira.expect(requestTo(startsWith(WORKLOGS_URL + "/10101?")))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThatThrownBy(() -> client.update(target(), "10101",
        new JiraWorklogEntry(LocalDate.of(2026, 1, 15), 120)))
        .isInstanceOf(JiraWorklogNotFoundException.class);
  }

  @Test
  void testDeleteRemovesTheWorklog() {
    jira.expect(requestTo(startsWith(WORKLOGS_URL + "/10101?")))
        .andExpect(method(HttpMethod.DELETE))
        .andExpect(queryParam("adjustEstimate", "leave"))
        .andRespond(withStatus(HttpStatus.NO_CONTENT));

    client.delete(target(), "10101");

    jira.verify();
  }

  @Test
  void testDeleteOfAnAlreadyRemovedWorklogSaysSo() {
    jira.expect(requestTo(startsWith(WORKLOGS_URL + "/10101?")))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThatThrownBy(() -> client.delete(target(), "10101"))
        .isInstanceOf(JiraWorklogNotFoundException.class);
  }

  private static JiraWorklogTarget target() {
    return new JiraWorklogTarget("https://mock-jira.com", "mockUser", "mockPassword", "MOCK-1");
  }

  private static String basicAuth(String username, String password) {
    return "Basic " + Base64.getEncoder()
        .encodeToString((username + ":" + password).getBytes(UTF_8));
  }
}
