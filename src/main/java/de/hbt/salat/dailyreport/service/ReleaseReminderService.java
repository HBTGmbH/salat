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
 * Reminds whoever has not released a month (#215, #1557): first on its last working day, to the
 * person alone, then on every working day of the following month until the release reaches what is
 * due, with the people leads of the contract in CC. A working day is Monday to Friday and no public
 * holiday. A contract is reminded once a day at most: who was reminded of an older month in the
 * morning hears of the running one the next day.
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
   * The contracts to remind of the running month for the first time: on its last working day those
   * whose release does not reach its end, on any other day none.
   */
  @Transactional(readOnly = true)
  public List<OverdueRelease> getContractsToRemindFirst() {
    var today = today();
    if (!isWorkingDay(today) || !isLastWorkingDayOfMonth(today)) {
      return List.of();
    }
    return notRemindedToday(employeecontractService.getRunningContractsReleasedBefore(DateUtils.getEndOfMonth(today)));
  }

  /** The contracts to remind again of the previous month: on a working day those whose release does not reach its end. */
  @Transactional(readOnly = true)
  public List<OverdueRelease> getContractsToRemindAgain() {
    var today = today();
    if (!isWorkingDay(today)) {
      return List.of();
    }
    var endOfPreviousMonth = DateUtils.getEndOfMonth(DateUtils.addMonths(today, -1));
    return notRemindedToday(employeecontractService.getRunningContractsReleasedBefore(endOfPreviousMonth));
  }

  /** Reminds the person of the contract of the running month, without CC; see {@link #remindAgain}. */
  public void remindFirst(OverdueRelease contract) {
    remind(contract, List.of(), "main.release.notification.releasereminderfirst.title");
  }

  /**
   * Reminds the person of the contract of the previous month, with the people leads in CC, and notes
   * the day. The note is flushed before the mail goes out, so that a second run on the same day fails
   * on the unique key instead of mailing again. A mail that fails rolls the note back: the next run
   * tries again.
   *
   * <p>A person without a login has no one to mail; the contract is skipped and logged.
   *
   * @throws de.hbt.salat.common.exception.BusinessRuleException {@code XX-0005} when the mail cannot be sent
   */
  public void remindAgain(OverdueRelease contract) {
    remind(contract, contract.peopleLeadSalatUserIds(), "main.release.notification.releasereminderauto.title");
  }

  private void remind(OverdueRelease contract, List<Long> cc, String titleKey) {
    if (contract.salatUserId() == null) {
      log.warn("Contract {} is overdue with its release, but its person has no login to remind", contract.employeecontractId());
      return;
    }
    releaseReminderRepository.saveAndFlush(new ReleaseReminder(contract.employeecontractId(), today()));
    notificationService.emitNotification(RELEASE_REMINDER_AUTO, List.of(contract.salatUserId()), cc,
        NotificationMessage.builder()
            .titleKey(titleKey)
            .descriptionKey(description(contract))
            .descriptionParams(descriptionParams(contract))
            .actionUrl(RELEASE_URL)
            .actionLabel(messages.getMessage("main.release.notification.releasereminder.action"))
            .build());
  }

  /** A contract never released is told so, not given a date that does not exist (#1560). */
  private static String description(OverdueRelease contract) {
    return contract.releasedUntil() == null
        ? "main.release.notification.releasereminderauto.never.description"
        : "main.release.notification.releasereminderauto.description";
  }

  private static List<String> descriptionParams(OverdueRelease contract) {
    var dueUntil = DateUtils.format(contract.dueUntil(), DATE_PATTERN);
    return contract.releasedUntil() == null
        ? List.of(dueUntil)
        : List.of(DateUtils.format(contract.releasedUntil(), DATE_PATTERN), dueUntil);
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

  /** Whether no working day of the month lies after {@code day}. */
  private boolean isLastWorkingDayOfMonth(LocalDate day) {
    var endOfMonth = DateUtils.getEndOfMonth(day);
    var holidays = publicholidayDAO.getPublicHolidaysBetween(day.plusDays(1), endOfMonth).stream()
        .map(Publicholiday::getRefdate)
        .collect(Collectors.toSet());
    return day.plusDays(1).datesUntil(endOfMonth.plusDays(1))
        .noneMatch(later -> isWeekday(later) && !holidays.contains(later));
  }

  /** Leaves out the contracts reminded today already: a second run after a restart mails nobody twice. */
  private List<OverdueRelease> notRemindedToday(List<OverdueRelease> contracts) {
    Set<Long> remindedToday = releaseReminderRepository.findEmployeecontractIdsByRemindedOn(today());
    return contracts.stream()
        .filter(contract -> !remindedToday.contains(contract.employeecontractId()))
        .toList();
  }
}
