package de.hbt.salat.jira.rest;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Trigger.MANUAL;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.jira.service.JiraReplicationScheduler;
import de.hbt.salat.jira.service.JiraReplicationService;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/jira/replication", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "jira-replication", description = "Trigger JIRA ticket replications on demand")
@Authorized(requiresManager = true)
public class JiraReplicationRestController {

  private final JiraReplicationScheduler scheduler;
  private final JiraReplicationService jiraReplicationService;
  private final AuthorizedUser authorizedUser;

  @Operation(summary = "Trigger all enabled replications")
  @PostMapping(path = "/run")
  @ResponseStatus(HttpStatus.OK)
  public void runAll() {
    scheduler.runAll(MANUAL);
  }

  @Operation(summary = "Trigger a specific replication by id")
  @PostMapping(path = "/run/{id}")
  @ResponseStatus(HttpStatus.OK)
  public void runOne(@PathVariable("id") long id) {
    jiraReplicationService.runRecorded(id, MANUAL);
  }

  /**
   * A run turned away — the JIRA connection switched off in this environment, the replication
   * switched off, or still running (#1458) — as {@code 409} with its code, rather than as a server
   * error. The authorization keeps its own answer ({@code AuthorizationExceptionHandler}).
   */
  @ExceptionHandler(BusinessRuleException.class)
  public ResponseEntity<ProblemDetail> handle(BusinessRuleException e) {
    var problemDetail = ProblemDetail.forStatus(CONFLICT);
    e.getMessages().stream().findFirst().ifPresent(message -> {
      problemDetail.setTitle(message.getErrorCode().getCode());
      problemDetail.setDetail(message.getErrorCode().getMessage());
    });
    return ResponseEntity.status(CONFLICT).contentType(APPLICATION_PROBLEM_JSON).body(problemDetail);
  }

}
