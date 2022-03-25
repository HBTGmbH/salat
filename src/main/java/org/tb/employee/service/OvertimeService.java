package org.tb.employee.service;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static org.tb.common.util.DateUtils.addDays;
import static org.tb.common.util.DateUtils.getBeginOfMonth;
import static org.tb.common.util.DateUtils.today;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.persistence.PublicholidayDAO;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.domain.Overtime;
import org.tb.employee.domain.OvertimeStatus;
import org.tb.employee.domain.OvertimeStatus.OvertimeStatusInfo;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.persistence.OvertimeDAO;

@Service
@RequiredArgsConstructor
public class OvertimeService {

  private final EmployeecontractDAO employeecontractDAO;
  private final PublicholidayDAO publicholidayDAO;
  private final TimereportDAO timereportDAO;
  private final OvertimeDAO overtimeDAO;

  public Optional<OvertimeStatus> calculateOvertime(long employeecontractId, boolean includeToday) {
    var employeecontract = employeecontractDAO.getEmployeeContractById(employeecontractId);
    if(employeecontract.getDailyWorkingTime().isZero()) {
      return Optional.empty();
    }
    var status = new OvertimeStatus();

    LocalDate totalBeginDate;
    LocalDate totalEndDate = includeToday ? today() : today().minusDays(1);
    LocalDate dynamicBeginDate;
    LocalDate dynamicEndDate = includeToday ? today() : today().minusDays(1);

    Duration overtimeTotal = Duration.ZERO;

    if (isStaticOvertimeAvailable(employeecontract)) {
      var staticBeginDate = employeecontract.getValidFrom();
      var staticEndDate = employeecontract.getReportAcceptanceDate();
      overtimeTotal = overtimeTotal.plus(employeecontract.getOvertimeStatic());
      dynamicBeginDate = addDays(staticEndDate, 1);
      totalBeginDate = staticBeginDate;
    } else {
      dynamicBeginDate = employeecontract.getValidFrom();
      totalBeginDate = dynamicBeginDate;
    }

    var overtimeDynamic = calculateOvertime(dynamicBeginDate, dynamicEndDate, employeecontract, true);
    overtimeTotal = overtimeTotal.plus(overtimeDynamic);
    status.setTotal(toStatusInfo(totalBeginDate, totalEndDate, overtimeTotal, employeecontract.getDailyWorkingTime()));

    //overtime this month to date
    LocalDate monthBeginDate = getBeginOfMonth(today());
    LocalDate monthEndDate = includeToday ? today() : today().minusDays(1);

    LocalDate validFrom = employeecontract.getValidFrom();
    if (validFrom.isAfter(monthBeginDate)) {
      monthBeginDate = validFrom;
    }
    LocalDate validUntil = employeecontract.getValidUntil();
    if (validUntil != null && validUntil.isBefore(monthEndDate)) {
      monthEndDate = validUntil;
    }
    if (!monthEndDate.isBefore(monthBeginDate)) {
      var monthOvertime = calculateOvertime(monthBeginDate, monthEndDate, employeecontract, false);
      status.setCurrentMonth(toStatusInfo(monthBeginDate, monthEndDate, monthOvertime, employeecontract.getDailyWorkingTime()));
    }

    return Optional.of(status);
  }

  private OvertimeStatusInfo toStatusInfo(LocalDate beginDate, LocalDate endDate, Duration overtime, Duration dailyWorkingTime) {
    OvertimeStatusInfo info = new OvertimeStatusInfo();
    info.setBegin(beginDate);
    info.setEnd(endDate);
    long days = overtime.dividedBy(dailyWorkingTime);
    var overtimeRest = overtime.minus(dailyWorkingTime.multipliedBy(days));
    long hours = overtimeRest.toHours();
    long minutes = overtimeRest.toMinutesPart();
    info.setDays(days);
    info.setHours(hours);
    info.setMinutes(minutes);
    return info;
  }

  private boolean isStaticOvertimeAvailable(Employeecontract employeecontract) {
    // TODO better to check the static overtime fields value?
    return employeecontract.getReportAcceptanceDate() != null &&
           employeecontract.getReportAcceptanceDate().isAfter(employeecontract.getValidFrom());
  }

  private Duration calculateOvertime(LocalDate requestedStart, LocalDate requestedEnd, Employeecontract employeecontract, boolean useOverTimeAdjustment) {

    final LocalDate start, end;
    // do not consider invalid(outside of the validity of the contract) days
    if (employeecontract.getValidFrom().isAfter(requestedStart)) {
      start = employeecontract.getValidFrom();
    } else {
      start = requestedStart;
    }
    if (employeecontract.getValidUntil() != null && employeecontract.getValidUntil().isBefore(requestedEnd)) {
      end = employeecontract.getValidUntil();
    } else {
      end = requestedEnd;
    }
    if (end.isBefore(start)) {
      return Duration.ZERO;
    }

    long numberOfWorkingDayHolidays = publicholidayDAO.getPublicHolidaysBetween(start, end)
        .stream()
        .map(Publicholiday::getRefdate)
        .map(LocalDate::getDayOfWeek)
        .filter(dayOfWeek -> dayOfWeek != SATURDAY && dayOfWeek != SUNDAY)
        .count();

    var expectedWorkingDaysCount = DateUtils.getWorkingDayDistance(start, end);
    // substract holidays
    expectedWorkingDaysCount -= numberOfWorkingDayHolidays;

    // calculate working time
    var dailyWorkingTime = employeecontract.getDailyWorkingTime();
    var expectedWorkingTime = dailyWorkingTime.multipliedBy(expectedWorkingDaysCount);
    var actualWorkingTime = timereportDAO
        .getTimereportsByDatesAndEmployeeContractId(employeecontract.getId(), start, end)
        .stream()
        .map(TimereportDTO::getDuration)
        .reduce(Duration.ZERO, Duration::plus);

    Duration overtime = actualWorkingTime.minus(expectedWorkingTime);
    if (useOverTimeAdjustment) {
      long overtimeAdjustmentMinutes = 0;
      var overtimeAdjustment = overtimeDAO.getOvertimesByEmployeeContractId(employeecontract.getId())
          .stream()
          .filter(o -> isOvertimeEffectiveBetween(start, end, o))
          .map(Overtime::getTimeMinutes)
          .reduce(Duration.ZERO, Duration::plus);
      overtime = overtime.plus(overtimeAdjustment);
    }
    return overtime;
  }

  // TODO introduce effective date in Overtime?
  private boolean isOvertimeEffectiveBetween(LocalDate start, LocalDate end, Overtime overtime) {
    return !overtime.getCreated().isAfter(end.plusDays(1).atStartOfDay()) &&
           !overtime.getCreated().isBefore(start.atStartOfDay());
  }

}
