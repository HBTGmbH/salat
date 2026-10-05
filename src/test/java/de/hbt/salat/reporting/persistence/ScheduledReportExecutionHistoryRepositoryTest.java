package de.hbt.salat.reporting.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.reporting.domain.ReportDefinition;
import de.hbt.salat.reporting.domain.ScheduledReportExecutionHistory;
import de.hbt.salat.reporting.domain.ScheduledReportJob;

/** An entry of the execution history outlives the job it names (#1366). */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ScheduledReportExecutionHistoryRepositoryTest {

  @Autowired
  private ScheduledReportExecutionHistoryRepository historyRepository;

  @Autowired
  private ScheduledReportJobRepository jobRepository;

  @Autowired
  private TestEntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
  }

  @Test
  void an_entry_keeps_its_names_when_its_job_is_deleted() {
    var report = new ReportDefinition();
    report.setName("Stunden je Auftrag");
    report.setSql("select 1");
    entityManager.persist(report);
    var job = new ScheduledReportJob();
    job.setReportDefinition(report);
    job.setName("Montags");
    job.setRecipientEmails("team@example.org");
    jobRepository.save(job);
    var entry = historyRepository.save(ScheduledReportExecutionHistory.builder()
        .job(job)
        .jobName(job.getName())
        .reportDefinition(report)
        .reportDefinitionName(report.getName())
        .executedAt(LocalDateTime.of(2026, 10, 5, 6, 0))
        .success(true)
        .build());
    entityManager.flush();
    entityManager.clear();

    jobRepository.deleteById(job.getId());
    entityManager.flush();
    entityManager.clear();

    var kept = historyRepository.findById(entry.getId()).orElseThrow();
    assertThat(kept.getJob()).isNull();
    assertThat(kept.getJobName()).isEqualTo("Montags");
    assertThat(kept.getReportDefinition().getId()).isEqualTo(report.getId());
    assertThat(kept.getReportDefinitionName()).isEqualTo("Stunden je Auftrag");
  }

}
