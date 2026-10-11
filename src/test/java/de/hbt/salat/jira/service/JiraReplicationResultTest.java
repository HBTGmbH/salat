package de.hbt.salat.jira.service;

import static org.assertj.core.api.Assertions.assertThat;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.FAILED;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.INCOMPLETE;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.SUCCEEDED;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * How a replication run ends (#1325): single worklogs that could not be written make it incomplete,
 * an exception or an issue that could not be stored make it fail.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraReplicationResultTest {

  @Test
  void a_clean_run_succeeded() {
    assertThat(result(0, null, 0).status()).isEqualTo(SUCCEEDED);
  }

  @Test
  void single_failed_worklogs_make_the_run_incomplete() {
    var result = result(0, null, 3);

    assertThat(result.status()).isEqualTo(INCOMPLETE);
    assertThat(result.summary()).isEqualTo("5 Tickets geholt, 4 geschrieben. 3 Worklogs nicht abgeglichen.");
  }

  @Test
  void a_sync_that_broke_off_fails_the_run() {
    assertThat(result(0, "Zeitüberschreitung", 0).status()).isEqualTo(FAILED);
  }

  @Test
  void an_issue_that_could_not_be_stored_fails_the_run_even_with_failed_worklogs() {
    assertThat(result(1, null, 3).status()).isEqualTo(FAILED);
  }

  private static JiraReplicationResult result(int failed, String worklogSyncError, int failedWorklogs) {
    return new JiraReplicationResult(5, 4, failed, 0, List.of(), worklogSyncError, failedWorklogs);
  }

}
