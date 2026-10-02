package de.hbt.salat.jira.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The threads a replication started by hand runs on (#1282) — see {@code JiraReplicationLauncher}.
 *
 * <p><b>Deliberately not the shared async pool.</b> That one has a single thread and also carries
 * the statistics updates; a replication fetching page after page from a foreign system would hold
 * them up for its whole duration. The same reasoning stands at {@code ETLExecutorConfiguration}.
 *
 * <p><b>Two threads, queue of length zero.</b> The lock is the {@code RUNNING} row per replication;
 * two different replications get along, so two managers trying out two configs do not wait for each
 * other. A third submission is rejected rather than queued: a run waiting unseen in a queue would
 * stand as "running" in the list without running.
 */
@Configuration
public class JiraExecutorConfiguration {

  /** Bean name of the executor a replication started by hand runs on. */
  public static final String JIRA_REPLICATION_TASK_EXECUTOR = "jiraReplicationTaskExecutor";

  @Bean(JIRA_REPLICATION_TASK_EXECUTOR)
  public ThreadPoolTaskExecutor jiraReplicationTaskExecutor() {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix("JiraRun-");
    return executor;
  }

}
