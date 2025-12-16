package org.tb.jira.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.jira.service.JiraReplicationScheduler;
import org.tb.jira.service.JiraReplicationService;

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
    scheduler.runAll();
  }

  @Operation(summary = "Trigger a specific replication by id")
  @PostMapping(path = "/run/{id}")
  @ResponseStatus(HttpStatus.OK)
  public void runOne(@PathVariable("id") long id) {
    jiraReplicationService.runReplication(id);
  }

}
