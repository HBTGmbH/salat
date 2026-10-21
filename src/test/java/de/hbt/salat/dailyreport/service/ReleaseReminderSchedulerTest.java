package de.hbt.salat.dailyreport.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.employee.domain.OverdueRelease;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** One reminder that fails leaves the others alone (#215). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ReleaseReminderSchedulerTest {

  private final ReleaseReminderService service = mock(ReleaseReminderService.class);
  @SuppressWarnings("unchecked")
  private final ObjectProvider<AuthorizedUser> authorizedUserProvider = mock(ObjectProvider.class);
  private final AuthorizedUser authorizedUser = mock(AuthorizedUser.class);
  private final ReleaseReminderScheduler scheduler = new ReleaseReminderScheduler(service, authorizedUserProvider);

  @Test
  void runs_as_job_and_reminds_the_next_contract_after_one_that_failed() {
    var failing = overdue(1L);
    var next = overdue(2L);
    when(authorizedUserProvider.getObject()).thenReturn(authorizedUser);
    when(service.getContractsToRemindToday()).thenReturn(List.of(failing, next));
    doThrow(new RuntimeException("mail server down")).when(service).remind(failing);

    scheduler.run();

    verify(authorizedUser).initForJob();
    verify(service).remind(next);
  }

  private static OverdueRelease overdue(long contractId) {
    return new OverdueRelease(contractId, 42L, LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 30), List.of());
  }
}
