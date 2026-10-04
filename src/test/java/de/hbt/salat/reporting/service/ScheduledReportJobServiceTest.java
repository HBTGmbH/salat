package de.hbt.salat.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.context.ApplicationEventPublisher;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.reporting.auth.ReportAuthorization;
import de.hbt.salat.reporting.domain.ScheduledReportJob;
import de.hbt.salat.reporting.persistence.ScheduledReportExecutionHistoryRepository;
import de.hbt.salat.reporting.persistence.ScheduledReportJobRepository;

/**
 * A scheduled job belongs to the login it was created under, by the id of that login (#1330).
 * Whoever is not a manager sees, changes and deletes only their own.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ScheduledReportJobServiceTest {

  private static final long OWNER = 5L;

  @Mock
  private ScheduledReportJobRepository scheduledReportJobRepository;
  @Mock
  private ReportEmailService reportEmailService;
  @Mock
  private ScheduledReportExecutionHistoryRepository historyRepository;
  @Mock
  private ApplicationEventPublisher applicationEventPublisher;
  @Mock
  private AuthorizedUser authorizedUser;
  @Mock
  private ReportAuthorization reportAuthorization;

  @InjectMocks
  private ScheduledReportJobService scheduledReportJobService;

  @Test
  void a_people_lead_sees_the_jobs_of_their_login() {
    when(authorizedUser.getEffectiveUserId()).thenReturn(OWNER);
    var own = job(1L, OWNER);
    when(scheduledReportJobRepository.findByOwnerUserId(OWNER)).thenReturn(List.of(own));

    assertThat(scheduledReportJobService.getAllJobs()).containsExactly(own);
  }

  @Test
  void without_a_login_there_are_no_own_jobs() {
    when(authorizedUser.getEffectiveUserId()).thenReturn(null);

    assertThat(scheduledReportJobService.getAllJobs()).isEmpty();
  }

  @Test
  void a_new_job_belongs_to_the_login_it_is_created_under() {
    when(authorizedUser.getEffectiveUserId()).thenReturn(OWNER);
    when(scheduledReportJobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var saved = scheduledReportJobService.createJob(new ScheduledReportJob());

    assertThat(saved.getOwnerUserId()).isEqualTo(OWNER);
  }

  @Test
  void the_owner_deletes_their_job() {
    when(scheduledReportJobRepository.findById(1L)).thenReturn(Optional.of(job(1L, OWNER)));
    when(reportAuthorization.isOwnedByCurrentUser(OWNER)).thenReturn(true);

    scheduledReportJobService.deleteJob(1L);

    verify(scheduledReportJobRepository).deleteById(1L);
  }

  @Test
  void somebody_else_does_not_even_if_createdby_carries_their_login_name() {
    // a login name given out again must not inherit the job (#1330)
    var job = job(1L, OWNER);
    ReflectionTestUtils.setField(job, "createdby", "pl");
    when(authorizedUser.getEffectiveLoginSign()).thenReturn("pl");
    when(scheduledReportJobRepository.findById(1L)).thenReturn(Optional.of(job));
    when(reportAuthorization.isOwnedByCurrentUser(OWNER)).thenReturn(false);

    assertThatThrownBy(() -> scheduledReportJobService.deleteJob(1L)).isInstanceOf(AuthorizationException.class);
    verify(scheduledReportJobRepository, never()).deleteById(any());
  }

  @Test
  void a_manager_deletes_any_job() {
    when(authorizedUser.isManager()).thenReturn(true);
    when(scheduledReportJobRepository.findById(1L)).thenReturn(Optional.of(job(1L, null)));

    scheduledReportJobService.deleteJob(1L);

    verify(scheduledReportJobRepository).deleteById(1L);
  }

  private static ScheduledReportJob job(long id, Long ownerUserId) {
    var job = new ScheduledReportJob(id);
    job.setOwnerUserId(ownerUserId);
    return job;
  }
}
