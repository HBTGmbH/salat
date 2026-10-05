package de.hbt.salat.jira.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static de.hbt.salat.jira.domain.JiraApiFlavor.SERVER;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.SUCCEEDED;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Trigger.SCHEDULED;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.jira.OrderTree;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.jira.domain.JiraReplicationRun;
import de.hbt.salat.jira.persistence.JiraReplicationConfigRepository;
import de.hbt.salat.jira.persistence.JiraReplicationRunRepository;

/**
 * The run history of the replications does not grow without end (#1282): every enabled replication
 * writes a row an hour.
 */
@DataJpaTest
@FixedClock(JiraReplicationRunCleanupServiceTest.NOW_TEXT)
@Import({ JiraReplicationRunCleanupService.class, SalatProperties.class, AuthorizedUserAuditorAware.class })
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraReplicationRunCleanupServiceTest {

  static final String NOW_TEXT = "2026-10-02T02:50:00";
  private static final LocalDateTime NOW = LocalDateTime.parse(NOW_TEXT);

  @Autowired
  private JiraReplicationRunCleanupService cleanupService;

  @Autowired
  private JiraReplicationRunRepository runRepository;

  @Autowired
  private JiraReplicationConfigRepository configRepository;

  @Autowired
  private SalatProperties salatProperties;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  private JiraReplicationConfig replication;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
    runRepository.deleteAll();
    replication = replication("Aufräumen");
    salatProperties.getJira().getHistory().setRetentionDays(14);
  }

  @Test
  void deletes_only_runs_older_than_the_retention_period() {
    save(NOW.minusDays(15));
    save(NOW.minusDays(200));
    var withinRetention = save(NOW.minusDays(13));
    var today = save(NOW);

    cleanupService.deleteExpiredRuns();

    assertThat(runRepository.findAll())
        .extracting(JiraReplicationRun::getId)
        .containsExactlyInAnyOrder(withinRetention.getId(), today.getId());
  }

  @Test
  void respects_a_changed_retention_period() {
    salatProperties.getJira().getHistory().setRetentionDays(90);
    save(NOW.minusDays(91));
    var kept = save(NOW.minusDays(89));

    cleanupService.deleteExpiredRuns();

    assertThat(runRepository.findAll())
        .extracting(JiraReplicationRun::getId)
        .containsExactly(kept.getId());
  }

  private JiraReplicationRun save(LocalDateTime startedAt) {
    return runRepository.save(JiraReplicationRun.builder()
        .replication(replication)
        .startedAt(startedAt)
        .finishedAt(startedAt.plusSeconds(4))
        .status(SUCCEEDED)
        .triggeredBy(SCHEDULED)
        .message("fertig")
        .build());
  }

  private JiraReplicationConfig replication(String name) {
    var config = new JiraReplicationConfig();
    config.setName(name);
    config.setCustomerorder(new OrderTree(entityManager).customerorder("SCOPE"));
    config.setBaseUrl("http://jira.example");
    config.setApiFlavor(SERVER);
    config.setUsername("user");
    config.setPassword("secret");
    config.setJql("project = RUN");
    config.setEnabled(true);
    return configRepository.save(config);
  }

}
