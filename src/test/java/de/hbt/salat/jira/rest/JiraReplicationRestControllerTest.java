package de.hbt.salat.jira.rest;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Trigger.MANUAL;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.jira.service.JiraReplicationScheduler;
import de.hbt.salat.jira.service.JiraReplicationService;

/**
 * A run turned away over the REST interface (#1458) is answered with its code, not as a server error.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraReplicationRestControllerTest {

  @Mock
  private JiraReplicationScheduler scheduler;

  @Mock
  private JiraReplicationService replicationService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    var controller = new JiraReplicationRestController(scheduler, replicationService, mock(AuthorizedUser.class));
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void with_jira_switched_off_all_replications_are_refused_with_the_code() throws Exception {
    doThrow(new BusinessRuleException(ErrorCode.JI_DISABLED)).when(scheduler).runAll(MANUAL);

    mockMvc.perform(post("/api/jira/replication/run"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("JI-0054"));
  }

  @Test
  void with_jira_switched_off_a_single_replication_is_refused_with_the_code() throws Exception {
    when(replicationService.runRecorded(7L, MANUAL)).thenThrow(new BusinessRuleException(ErrorCode.JI_DISABLED));

    mockMvc.perform(post("/api/jira/replication/run/7"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("JI-0054"));
  }

  @Test
  void a_switched_off_replication_is_refused_with_the_code() throws Exception {
    when(replicationService.runRecorded(7L, MANUAL))
        .thenThrow(new BusinessRuleException(ErrorCode.JI_REPLICATION_DISABLED));

    mockMvc.perform(post("/api/jira/replication/run/7"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("JI-0055"));
  }

  @Test
  void with_the_defaults_a_run_is_started() throws Exception {
    mockMvc.perform(post("/api/jira/replication/run"))
        .andExpect(status().isOk());
  }
}
