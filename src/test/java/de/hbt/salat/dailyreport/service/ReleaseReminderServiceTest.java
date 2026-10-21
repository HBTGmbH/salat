package de.hbt.salat.dailyreport.service;

import static de.hbt.salat.dailyreport.service.DailyReportNotificationKinds.RELEASE_REMINDER_AUTO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.util.ClockProvider;
import de.hbt.salat.dailyreport.domain.LastReleaseReminder;
import de.hbt.salat.dailyreport.domain.Publicholiday;
import de.hbt.salat.dailyreport.domain.ReleaseReminder;
import de.hbt.salat.dailyreport.persistence.PublicholidayDAO;
import de.hbt.salat.dailyreport.persistence.ReleaseReminderRepository;
import de.hbt.salat.employee.domain.OverdueRelease;
import de.hbt.salat.employee.event.EmployeecontractDeleteEvent;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.notification.domain.NotificationMessage;
import de.hbt.salat.notification.service.NotificationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.MessageSourceAccessor;

/**
 * The automatic reminder to release the previous month (#215): on every working day from the first
 * of the month on, from the second working day on with the people leads in CC, never twice a day.
 *
 * <p>October 2026 begins on a Thursday; the 3rd, a Saturday, is a public holiday in the test.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ReleaseReminderServiceTest {

  private static final LocalDate THURSDAY_FIRST = LocalDate.of(2026, 10, 1);
  private static final LocalDate FRIDAY_SECOND = LocalDate.of(2026, 10, 2);
  private static final LocalDate SATURDAY = LocalDate.of(2026, 10, 3);
  private static final long CONTRACT_ID = 7L;
  private static final long PERSON = 42L;
  private static final List<Long> PEOPLE_LEADS = List.of(43L, 44L);

  @Mock
  private EmployeecontractService employeecontractService;
  @Mock
  private PublicholidayDAO publicholidayDAO;
  @Mock
  private ReleaseReminderRepository releaseReminderRepository;
  @Mock
  private NotificationService notificationService;
  @Mock
  private MessageSourceAccessor messages;

  @InjectMocks
  private ReleaseReminderService service;

  @BeforeEach
  void noHolidaysUnlessSaidOtherwise() {
    lenient().when(publicholidayDAO.getPublicHoliday(any())).thenReturn(Optional.empty());
    lenient().when(publicholidayDAO.getPublicHolidaysBetween(any(), any())).thenReturn(List.of());
    lenient().when(messages.getMessage(anyString())).thenReturn("Zur Freigabe");
  }

  @AfterEach
  void resetTheClock() {
    ClockProvider.reset();
  }

  @Nested
  class WhomToRemind {

    @Test
    void on_a_working_day_every_overdue_contract_not_yet_reminded_today() {
      today(THURSDAY_FIRST);
      var reminded = overdue(8L);
      when(releaseReminderRepository.findEmployeecontractIdsByRemindedOn(THURSDAY_FIRST)).thenReturn(Set.of(8L));
      when(employeecontractService.getRunningContractsWithOverdueRelease()).thenReturn(List.of(overdue(CONTRACT_ID), reminded));

      assertThat(service.getContractsToRemindToday()).extracting(OverdueRelease::employeecontractId)
          .containsExactly(CONTRACT_ID);
    }

    @Test
    void nobody_at_the_weekend() {
      today(SATURDAY);

      assertThat(service.getContractsToRemindToday()).isEmpty();
      verifyNoInteractions(employeecontractService);
    }

    @Test
    void nobody_on_a_public_holiday() {
      today(FRIDAY_SECOND);
      when(publicholidayDAO.getPublicHoliday(FRIDAY_SECOND)).thenReturn(Optional.of(new Publicholiday(FRIDAY_SECOND, "Feiertag")));

      assertThat(service.getContractsToRemindToday()).isEmpty();
      verifyNoInteractions(employeecontractService);
    }
  }

  @Nested
  class Reminding {

    @Test
    void on_the_first_working_day_the_mail_goes_to_the_person_alone_and_the_day_is_noted() {
      today(THURSDAY_FIRST);

      service.remind(overdue(CONTRACT_ID));

      var message = ArgumentCaptor.forClass(NotificationMessage.class);
      verify(notificationService).emitNotification(eq(RELEASE_REMINDER_AUTO), eq(List.of(PERSON)), eq(List.of()), message.capture());
      assertThat(message.getValue().descriptionParams()).containsExactly("31.08.2026", "30.09.2026");
      assertThat(message.getValue().actionUrl()).isEqualTo("/release");
      // no person reminds, so the mail comes from the address for notifications
      assertThat(message.getValue().sender()).isNull();
      var noted = ArgumentCaptor.forClass(ReleaseReminder.class);
      verify(releaseReminderRepository).saveAndFlush(noted.capture());
      assertThat(noted.getValue().getEmployeecontractId()).isEqualTo(CONTRACT_ID);
      assertThat(noted.getValue().getRemindedOn()).isEqualTo(THURSDAY_FIRST);
    }

    @Test
    void from_the_second_working_day_on_the_people_leads_read_along() {
      today(FRIDAY_SECOND);

      service.remind(overdue(CONTRACT_ID));

      verify(notificationService).emitNotification(eq(RELEASE_REMINDER_AUTO), eq(List.of(PERSON)), eq(PEOPLE_LEADS), any());
    }

    /** The 1st is a holiday and the 2nd lies at the weekend: Monday the 4th is the first working day. */
    @Test
    void the_first_working_day_comes_after_a_holiday_and_the_weekend() {
      var monday = LocalDate.of(2027, 1, 4);
      today(monday);
      when(publicholidayDAO.getPublicHolidaysBetween(LocalDate.of(2027, 1, 1), monday))
          .thenReturn(List.of(new Publicholiday(LocalDate.of(2027, 1, 1), "Neujahr")));

      service.remind(overdue(CONTRACT_ID));

      verify(notificationService).emitNotification(eq(RELEASE_REMINDER_AUTO), eq(List.of(PERSON)), eq(List.of()), any());
    }

    @Test
    void a_person_without_a_login_is_skipped_without_a_note() {
      today(THURSDAY_FIRST);

      service.remind(new OverdueRelease(CONTRACT_ID, null, LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 30), PEOPLE_LEADS));

      verifyNoInteractions(notificationService, releaseReminderRepository);
    }

    /** The scheduler logs it and goes on; the transaction takes the note back, and the next run tries again. */
    @Test
    void a_mail_that_cannot_be_sent_fails_the_reminder() {
      today(THURSDAY_FIRST);
      doThrow(new BusinessRuleException(ErrorCode.XX_MAIL_NOT_SENT, "Anna"))
          .when(notificationService).emitNotification(any(), any(), any(), any());

      assertThatThrownBy(() -> service.remind(overdue(CONTRACT_ID))).isInstanceOf(BusinessRuleException.class);
    }
  }

  @Test
  void the_acceptance_learns_the_last_day_each_contract_was_reminded() {
    when(releaseReminderRepository.findLastByEmployeecontractIdIn(List.of(CONTRACT_ID, 8L)))
        .thenReturn(List.of(new LastReleaseReminder(CONTRACT_ID, FRIDAY_SECOND)));

    assertThat(service.getLastReminders(List.of(CONTRACT_ID, 8L))).isEqualTo(Map.of(CONTRACT_ID, FRIDAY_SECOND));
  }

  @Test
  void an_empty_list_asks_no_query() {
    assertThat(service.getLastReminders(List.of())).isEmpty();
    verifyNoInteractions(releaseReminderRepository);
  }

  @Test
  void a_deleted_contract_takes_its_notes_along() {
    service.onEmployeecontractDelete(new EmployeecontractDeleteEvent(CONTRACT_ID));

    verify(releaseReminderRepository).deleteByEmployeecontractId(CONTRACT_ID);
  }

  private static OverdueRelease overdue(long contractId) {
    return new OverdueRelease(contractId, PERSON, LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 30), PEOPLE_LEADS);
  }

  private static void today(LocalDate day) {
    var zone = ZoneId.systemDefault();
    ClockProvider.setClock(Clock.fixed(day.atTime(12, 0).atZone(zone).toInstant(), zone));
  }
}
