package de.hbt.salat.jira.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import de.hbt.salat.common.util.ClockProvider;

/**
 * Everything the two worklog clients share (#1007). They differ in exactly two things: the API
 * version in the path, and how a comment is shaped — Server takes plain text, Cloud takes ADF. The
 * text of the comment comes with the entry (#1408).
 */
@Slf4j
abstract class AbstractJiraWorklogClient extends AbstractJiraRestClient implements JiraWorklogClient {

  /**
   * JIRA insists on this shape for {@code started} — note the offset without a colon, which
   * {@code ISO_OFFSET_DATE_TIME} would write. A value it cannot parse is answered with a 400 that
   * says nothing about the field.
   */
  private static final DateTimeFormatter STARTED = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

  /**
   * Midday rather than midnight. The time of day carries no meaning — the worklog stands for a
   * whole day — but it has to be the same on every run, or a re-run would move the entry. Midday
   * also keeps the day intact for a reader in a neighbouring time zone, which midnight would not.
   */
  private static final int STARTED_HOUR = 12;

  protected AbstractJiraWorklogClient(RestClient.Builder restClientBuilder) {
    super(restClientBuilder);
  }

  /** {@code rest/api/2} or {@code rest/api/3}, whichever flavour this client speaks. */
  protected abstract String apiPath();

  /**
   * The comment of the worklog (#1408), see {@link JiraWorklogEntry#WORKLOG_COMMENT}: plain text for
   * Server, an ADF document for Cloud.
   */
  protected abstract Object comment(String text);

  @Override
  public String create(JiraWorklogTarget target, JiraWorklogEntry entry) {
    var url = worklogsUrl(target);
    log.info("JIRA worklog create: baseUrl={}, issue={}, date={}, minutes={}",
        target.baseUrl(), target.issueKey(), entry.workDate(), entry.minutes());

    JiraWorklogResponse response;
    try {
      response = clientFor(target.credentials())
          .post().uri(url)
          .contentType(MediaType.APPLICATION_JSON)
          .body(body(entry))
          .retrieve()
          .body(JiraWorklogResponse.class);
    } catch (RestClientException ex) {
      log.error("JIRA worklog create failed: baseUrl={}, issue={}, date={}",
          target.baseUrl(), target.issueKey(), entry.workDate(), ex);
      throw ex;
    }

    if (response == null || response.getId() == null || response.getId().isBlank()) {
      // Without the id the worklog cannot be recognised as ours again, and the next run would write
      // a second one next to it. Failing here leaves the row unwritten, so the next run retries.
      throw new IllegalStateException("JIRA answered the created worklog of issue "
          + target.issueKey() + " without an id");
    }
    return response.getId();
  }

  @Override
  public void update(JiraWorklogTarget target, String worklogId, JiraWorklogEntry entry) {
    var url = worklogUrl(target, worklogId);
    log.info("JIRA worklog update: baseUrl={}, issue={}, worklogId={}, date={}, minutes={}",
        target.baseUrl(), target.issueKey(), worklogId, entry.workDate(), entry.minutes());
    try {
      clientFor(target.credentials())
          .put().uri(url)
          .contentType(MediaType.APPLICATION_JSON)
          .body(body(entry))
          .retrieve()
          .toBodilessEntity();
    } catch (HttpClientErrorException.NotFound ex) {
      throw new JiraWorklogNotFoundException(target.issueKey(), worklogId, ex);
    } catch (RestClientException ex) {
      log.error("JIRA worklog update failed: baseUrl={}, issue={}, worklogId={}",
          target.baseUrl(), target.issueKey(), worklogId, ex);
      throw ex;
    }
  }

  @Override
  public void delete(JiraWorklogTarget target, String worklogId) {
    var url = worklogUrl(target, worklogId);
    log.info("JIRA worklog delete: baseUrl={}, issue={}, worklogId={}",
        target.baseUrl(), target.issueKey(), worklogId);
    try {
      clientFor(target.credentials())
          .delete().uri(url)
          .retrieve()
          .toBodilessEntity();
    } catch (HttpClientErrorException.NotFound ex) {
      throw new JiraWorklogNotFoundException(target.issueKey(), worklogId, ex);
    } catch (RestClientException ex) {
      log.error("JIRA worklog delete failed: baseUrl={}, issue={}, worklogId={}",
          target.baseUrl(), target.issueKey(), worklogId, ex);
      throw ex;
    }
  }

  private Map<String, Object> body(JiraWorklogEntry entry) {
    return Map.of(
        "started", started(entry.workDate()),
        "timeSpentSeconds", entry.minutes() * 60L,
        "comment", comment(entry.comment()));
  }

  /**
   * {@code adjustEstimate=leave}: without it every worklog takes its time off the issue's remaining
   * estimate, so a re-run would eat the planning of a ticket nobody meant to touch.
   * {@code notifyUsers=false}: a catch-up run over many days would otherwise set off one mail per
   * worklog to everybody watching the issue.
   */
  private String worklogsUrl(JiraWorklogTarget target) {
    return UriComponentsBuilder
        .fromUriString(endpointUrl(target.baseUrl(), apiPath() + "/issue/" + target.issueKey() + "/worklog"))
        .queryParam("adjustEstimate", "leave")
        .queryParam("notifyUsers", "false")
        .build().encode().toUriString();
  }

  private String worklogUrl(JiraWorklogTarget target, String worklogId) {
    return UriComponentsBuilder
        .fromUriString(endpointUrl(target.baseUrl(),
            apiPath() + "/issue/" + target.issueKey() + "/worklog/" + worklogId))
        .queryParam("adjustEstimate", "leave")
        .queryParam("notifyUsers", "false")
        .build().encode().toUriString();
  }

  private static String started(LocalDate workDate) {
    return workDate.atTime(STARTED_HOUR, 0)
        .atZone(ClockProvider.getClock().getZone())
        .format(STARTED);
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class JiraWorklogResponse {

    private String id;
  }
}
