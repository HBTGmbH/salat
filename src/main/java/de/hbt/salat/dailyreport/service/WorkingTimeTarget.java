package de.hbt.salat.dailyreport.service;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.function.BiFunction;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.dailyreport.domain.Publicholiday;
import de.hbt.salat.employee.domain.Employeecontract;

/**
 * The working time a contract asks for in a range of days: its daily working time on every weekday
 * within its validity that is not a public holiday. Weekends, public holidays falling on a weekday and
 * days outside the contract carry no target (#857).
 *
 * <p>One calculation for every question about a target, so that they cannot drift apart: the overtime
 * account and the target of a day ({@link OvertimeService#calculateWorkingTimeTarget}), the duration the
 * booking form proposes for an absence (#1214) and the limit of a day with an absence that saving a
 * booking checks (#397). A static function rather than a bean: {@link OvertimeService} reads bookings
 * through {@link TimereportService}, which in turn checks the limit, and a bean would close that cycle.
 * The calling service reads the public holidays and hands in its query.
 */
final class WorkingTimeTarget {

  private WorkingTimeTarget() {
  }

  /**
   * @param publicHolidaysBetween the public holidays from the first to the last day, both included
   */
  static Duration between(Employeecontract employeecontract, LocalDate requestedStart, LocalDate requestedEnd,
      BiFunction<LocalDate, LocalDate, List<Publicholiday>> publicHolidaysBetween) {
    // do not consider invalid(outside of the validity of the contract) days
    final LocalDate start = employeecontract.getValidFrom().isAfter(requestedStart)
        ? employeecontract.getValidFrom() : requestedStart;
    final LocalDate end = employeecontract.getValidUntil() != null && employeecontract.getValidUntil().isBefore(requestedEnd)
        ? employeecontract.getValidUntil() : requestedEnd;
    if (end.isBefore(start)) {
      return Duration.ZERO;
    }
    long numberOfWorkingDayHolidays = publicHolidaysBetween.apply(start, end)
        .stream()
        .map(Publicholiday::getRefdate)
        .map(LocalDate::getDayOfWeek)
        .filter(dayOfWeek -> dayOfWeek != SATURDAY && dayOfWeek != SUNDAY)
        .count();

    var expectedWorkingDaysCount = DateUtils.getWorkingDayDistance(start, end) - numberOfWorkingDayHolidays;
    return employeecontract.getDailyWorkingTime().multipliedBy(expectedWorkingDaysCount);
  }
}
