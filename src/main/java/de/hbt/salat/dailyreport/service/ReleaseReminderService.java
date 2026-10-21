package de.hbt.salat.dailyreport.service;

import static de.hbt.salat.common.util.DateUtils.isWeekday;
import static de.hbt.salat.common.util.DateUtils.today;
import static de.hbt.salat.dailyreport.service.DailyReportNotificationKinds.RELEASE_REMINDER_AUTO;

import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.util.DateUtils;
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
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reminds whoever has not released the previous month (#215), on every working day from the first
 * of the month on, until the release reaches what is due. From the second working day on, the people
 * leads of the contract read along in CC. A working day is Monday to Friday and no public holiday.
 *
 * <p>The mail cannot be switched off, only the bell ({@link DailyReportNotificationKinds#RELEASE_REMINDER_AUTO}).
 * Contracts that ended before today are not reminded ({@link EmployeecontractService#getRunningContractsWithOverdueRelease}).
 *
 * <p>{@link ReleaseReminderScheduler} asks for the contracts and reminds each of them in a
 * transaction of its own: a mail that fails takes no other reminder with it.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
@Authorized(requiresManager = true)
public class ReleaseReminderService {

  private static final String RELEASE_URL = "/release";
  private static final String DATE_PATTERN = "dd.MM.yyyy";

  private final EmployeecontractService employeecontractService;
  private final PublicholidayDAO publicholidayDAO;
  private final ReleaseReminderRepository releaseReminderRepository;
  private final NotificationService notificationService;
  private final MessageSourceAccessor messages;

  /**
   * The contracts to remind today: none on a day that is no working day, and none that was reminded
   * today already — a second run after a restart mails nobody twice.
   */
  @Transactional(readOnly = true)
  public List<OverdueRelease> getContractsToRemindToday() {
    var today = today();
    if (!isWorkingDay(today)) {
      return List.of();
    }
    Set<Long> remindedToday = releaseReminderRepository.findEmployeecontractIdsByRemindedOn(today);
    return employeecontractService.getRunningContractsWithOverdueRelease().stream()
        .filter(contract -> !remindedToday.contains(contract.employeecontractId()))
        .toList();
  }

  /**
   * Reminds the person of the contract and notes the day. The note is flushed before the mail goes
   * out, so that a second run on the same day fails on the unique key instead of mailing again. A
   * mail that fails rolls the note back: the next run tries again.
   *
   * <p>A person without a login has no one to mail; the contract is skipped and logged.
   *
   * @throws de.hbt.salat.common.exception.BusinessRuleException {@code XX-0005} when the mail cannot be sent
   */
  public void remind(OverdueRelease contract) {
    if (contract.salatUserId() == null) {
      log.warn("Contract {} is overdue with its release, but its person has no login to remind", contract.employeecontractId());
      return;
    }
    var today = today();
    releaseReminderRepository.saveAndFlush(new ReleaseReminder(contract.employeecontractId(), today));
    var cc = isFirstWorkingDayOfMonth(today) ? List.<Long>of() : contract.peopleLeadSalatUserIds();
    notificationService.emitNotification(RELEASE_REMINDER_AUTO, List.of(contract.salatUserId()), cc,
        NotificationMessage.builder()
            .titleKey("main.release.notification.releasereminderauto.title")
            .descriptionKey("main.release.notification.releasereminderauto.description")
            .descriptionParams(List.of(DateUtils.format(contract.releasedUntil(), DATE_PATTERN),
                DateUtils.format(contract.dueUntil(), DATE_PATTERN)))
            .actionUrl(RELEASE_URL)
            .actionLabel(messages.getMessage("main.release.notification.releasereminder.action"))
            .build());
  }

  /** The day each of the contracts was last reminded automatically; a contract never reminded is missing. */
  @Authorized(requiresPeopleLead = true)
  @Transactional(readOnly = true)
  public Map<Long, LocalDate> getLastReminders(Collection<Long> employeecontractIds) {
    if (employeecontractIds.isEmpty()) {
      return Map.of();
    }
    return releaseReminderRepository.findLastByEmployeecontractIdIn(employeecontractIds).stream()
        .collect(Collectors.toMap(LastReleaseReminder::employeecontractId, LastReleaseReminder::remindedOn));
  }

  @EventListener
  void onEmployeecontractDelete(EmployeecontractDeleteEvent event) {
    releaseReminderRepository.deleteByEmployeecontractId(event.getId());
  }

  private boolean isWorkingDay(LocalDate day) {
    return isWeekday(day) && publicholidayDAO.getPublicHoliday(day).isEmpty();
  }

  /** Whether no working day of the month lies before {@code day}. */
  private boolean isFirstWorkingDayOfMonth(LocalDate day) {
    var holidays = publicholidayDAO.getPublicHolidaysBetween(day.withDayOfMonth(1), day).stream()
        .map(Publicholiday::getRefdate)
        .collect(Collectors.toSet());
    return day.withDayOfMonth(1).datesUntil(day)
        .noneMatch(earlier -> isWeekday(earlier) && !holidays.contains(earlier));
  }
}
