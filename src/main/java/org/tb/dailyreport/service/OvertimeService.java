package org.tb.dailyreport.service;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.util.function.Function.identity;
import static org.tb.common.util.DateUtils.addDays;
import static org.tb.common.util.DateUtils.getBeginOfMonth;
import static org.tb.common.util.DateUtils.today;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.common.domain.AuditedEntity;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.OvertimeReport;
import org.tb.dailyreport.domain.OvertimeReportMonth;
import org.tb.dailyreport.domain.OvertimeReportTotal;
import org.tb.dailyreport.domain.OvertimeStatus;
import org.tb.dailyreport.domain.OvertimeStatus.OvertimeStatusInfo;
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.domain.Workingday.WorkingDayType;
import org.tb.dailyreport.event.TimereportsCreatedOrUpdatedEvent;
import org.tb.dailyreport.event.TimereportsDeletedEvent;
import org.tb.dailyreport.event.TimereportsDeletedEvent.TimereportDeleteId;
import org.tb.dailyreport.persistence.PublicholidayDAO;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.domain.Overtime;
import org.tb.employee.event.EmployeecontractUpdateEvent;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Employeeorder;
import org.tb.order.service.EmployeeorderService;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class OvertimeService {

  private final EmployeecontractDAO employeecontractDAO;
  private final PublicholidayDAO publicholidayDAO;
  private final TimereportDAO timereportDAO;
  private final WorkingdayDAO workingdayDAO;
  private final EmployeecontractService employeecontractService;
  private final TimereportService timereportService;
  private final EmployeeorderService employeeorderService;

  public Optional<Duration> calculateOvertime(long employeecontractId, LocalDate begin, LocalDate end) {
    var employeecontract = employeecontractDAO.getEmployeecontractById(employeecontractId);
    if(employeecontract.getDailyWorkingTime().isZero()) {
      return Optional.empty();
    }

    return Optional.of(calculateOvertime(begin, end, employeecontract, true).getDiff());
  }

  public Optional<OvertimeStatus> calculateOvertime(long employeecontractId, boolean includeToday) {
    var employeecontract = employeecontractDAO.getEmployeecontractById(employeecontractId);
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
    overtimeTotal = overtimeTotal.plus(overtimeDynamic.getDiff());
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
      var monthOvertimeInfo = calculateOvertime(monthBeginDate, monthEndDate, employeecontract, false);
      var compensatedOvertime = calculateOvertimeCompensation(employeecontractId, monthBeginDate, monthEndDate);
      var monthOvertime = monthOvertimeInfo.getDiff().plus(compensatedOvertime);
      status.setCurrentMonth(toStatusInfo(monthBeginDate, monthEndDate, monthOvertime, employeecontract.getDailyWorkingTime()));
    }

    return Optional.of(status);
  }

  /**
   * Calculates the (fictive) amount of time that results from reported overtime compensations in the given time period.
   *
   * @param begin begin of time period
   * @param end end of time period (inclusive)
   */
  public Duration calculateOvertimeCompensation(long employeecontractId, LocalDate begin, LocalDate end) {

    Employeecontract contract = employeecontractDAO.getEmployeecontractById(employeecontractId);
    if(contract == null) {
      throw new InvalidDataException(ErrorCode.EC_EMPLOYEE_CONTRACT_NOT_FOUND);
    }

    var timereports = timereportDAO.getTimereportsByDatesAndEmployeeContractId(employeecontractId, begin, end);

    // add dates with not worked or partially worked days
    var workingDays = workingdayDAO.getWorkingdaysByEmployeeContractId(employeecontractId, begin, end);
    var dates = workingDays
        .stream()
        .filter(workingday -> workingday.getType() == WorkingDayType.OVERTIME_COMPENSATED)
        .map(Workingday::getRefday)
        .distinct()
        .collect(Collectors.toSet());

    // sum reported time for every date with an overtime compensation
    var reportedMinutesPerDate = timereports
        .stream()
        .filter(timereport -> dates.contains(timereport.getReferenceday()))
        .collect(Collectors.groupingBy(TimereportDTO::getReferenceday, Collectors.summingLong(timereport -> timereport.getDuration().toMinutes())));
    reportedMinutesPerDate = new HashMap<>(reportedMinutesPerDate);
    for(var date : dates) {
      // ensure overtime is calculated for all dates, even if not time was logged - thats the case when WorkingDayType is used
      if(!reportedMinutesPerDate.containsKey(date)) {
        reportedMinutesPerDate.put(date, 0L);
      }
    }

    var dailyWorkingTime = contract.getDailyWorkingTime();

    var compensatedOvertime = reportedMinutesPerDate.values()
        .stream()
        .map(Duration::ofMinutes)
        .map(duration -> dailyWorkingTime.minus(duration))
        .filter(duration -> duration.isPositive()) // just use positive compensations
        .collect(Collectors.summingLong(Duration::toMinutes));

    return Duration.ofMinutes(compensatedOvertime);
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
    info.setNegative(overtime.isNegative());
    info.setDuration(overtime);
    return info;
  }

  private boolean isStaticOvertimeAvailable(Employeecontract employeecontract) {
    return employeecontract.getReportAcceptanceDate() != null;
  }

  public Duration calculateWorkingTimeTarget(long employeecontractId, LocalDate requestedStart, LocalDate requestedEnd) {
    var employeecontract = employeecontractDAO.getEmployeecontractById(employeecontractId);
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
    var dailyWorkingTime = employeecontract.getDailyWorkingTime();
    var workingTimeTarget = dailyWorkingTime.multipliedBy(expectedWorkingDaysCount);
    return workingTimeTarget;
  }

  private OvertimeInfo calculateOvertime(LocalDate requestedStart, LocalDate requestedEnd, Employeecontract employeecontract, boolean useOverTimeAdjustment) {

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
      return new OvertimeInfo(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO);
    }

    var workingTimeTarget = calculateWorkingTimeTarget(employeecontract.getId(), requestedStart, requestedEnd);
    var actualWorkingTime = timereportDAO
        .getTimereportsByDatesAndEmployeeContractId(employeecontract.getId(), requestedStart, requestedEnd)
        .stream()
        .map(TimereportDTO::getDuration)
        .reduce(Duration.ZERO, Duration::plus);
    var overtimeAdjustment = Duration.ZERO;

    Duration overtime = actualWorkingTime.minus(workingTimeTarget);
    if (useOverTimeAdjustment) {
      var overtimes = employeecontractService.getOvertimeAdjustmentsByEmployeeContractId(employeecontract.getId());
      overtimeAdjustment = overtimes
          .stream()
          .filter(o -> isOvertimeEffectiveBetween(start, end, o))
          .map(Overtime::getTimeMinutes)
          .reduce(Duration.ZERO, Duration::plus);

      // check if any adjustments have been made before the contract started if calculating including the contract validation start
      if(!start.isAfter(employeecontract.getValidFrom()) && !end.isBefore(employeecontract.getValidFrom())) {
        var overtimeAdjustmentBeforeContractStart = overtimes
            .stream()
            .filter(o -> isOvertimeEffectiveBefore(start, o))
            .map(Overtime::getTimeMinutes)
            .reduce(Duration.ZERO, Duration::plus);
        overtimeAdjustment = overtimeAdjustment.plus(overtimeAdjustmentBeforeContractStart);
      }

      overtime = overtime.plus(overtimeAdjustment);
    }

    return new OvertimeInfo(actualWorkingTime, overtimeAdjustment, actualWorkingTime.plus(overtimeAdjustment), workingTimeTarget, overtime);
  }

  // TODO introduce effective date in Overtime (adjustment)?
  private boolean isOvertimeEffectiveBetween(LocalDate start, LocalDate end, Overtime overtime) {
    return overtime.getCreated().isBefore(end.plusDays(1).atStartOfDay()) &&
           !overtime.getCreated().isBefore(start.atStartOfDay());
  }

  private boolean isOvertimeEffectiveBefore(LocalDate start, Overtime overtime) {
    return overtime.getCreated().isBefore(start.atStartOfDay());
  }

  public OvertimeReport createDetailedReportForEmployee(long employeecontractId) {
    var contract = employeecontractDAO.getEmployeecontractById(employeecontractId);

    // iterate over the months of the contract until today - calc overtime for every month seperately
    var today = DateUtils.today();
    var months = new ArrayList<OvertimeReportMonth>();
    var begin = contract.getValidFrom();
    var diffCumulative = Duration.ZERO;
    do {
      // if the month begin date is in the future, the related month should not be part of the report
      if(today.isBefore(begin)) {
        break;
      }
      // if the month begin date is outside the contract validation, the related month should not be part of the report
      if(contract.getValidUntil() != null && begin.isAfter(contract.getValidUntil())) {
        break;
      }
      YearMonth month = YearMonth.from(begin);
      LocalDate end   = month.atEndOfMonth();

      // check and correct end date if it would be in the future
      if(end.isAfter(today)) {
        end = today;
      }

      // check and correct end date if outside the contract validity
      if(contract.getValidUntil() != null && end.isAfter(contract.getValidUntil())) {
        end = contract.getValidUntil();
      }

      var overtimeInfo = calculateOvertime(begin, end, contract, true);
      diffCumulative = diffCumulative.plus(overtimeInfo.getDiff());
      months.add(
          OvertimeReportMonth.builder()
            .actual(overtimeInfo.getActual())
            .adjustment(overtimeInfo.getAdjustment())
            .sum(overtimeInfo.getSum())
            .target(overtimeInfo.getTarget())
            .diff(overtimeInfo.getDiff())
            .diffCumulative(diffCumulative)
            .yearMonth(month)
            .build()
      );

      begin = month.atDay(1).plusMonths(1);
    } while(true);

    Collections.sort(months, Comparator.comparing(OvertimeReportMonth::getYearMonth).reversed());

    var actual = months.stream().map(OvertimeReportMonth::getActual).reduce(Duration.ZERO, Duration::plus);
    var adjustment = months.stream().map(OvertimeReportMonth::getAdjustment).reduce(Duration.ZERO, Duration::plus);
    var sum = months.stream().map(OvertimeReportMonth::getSum).reduce(Duration.ZERO, Duration::plus);
    var target = months.stream().map(OvertimeReportMonth::getTarget).reduce(Duration.ZERO, Duration::plus);
    var diff = months.stream().map(OvertimeReportMonth::getDiff).reduce(Duration.ZERO, Duration::plus);
    var total = OvertimeReportTotal.builder()
        .actual(actual)
        .adjustment(adjustment)
        .sum(sum)
        .target(target)
        .diff(diff)
        .diffCumulative(diffCumulative)
        .build();

    return new OvertimeReport(total, months);
  }

  public void updateOvertimeStatic(Long employeecontractId) {
    var employeecontract = employeecontractDAO.getEmployeecontractById(employeecontractId);
    if(employeecontract.getReportAcceptanceDate() == null) {
      throw new IllegalArgumentException("employeecontract.reportAcceptanceDate must not be null for " + employeecontractId);
    }
    var newOvertimeStatic = calculateOvertime(employeecontractId,
        employeecontract.getValidFrom(),
        employeecontract.getReportAcceptanceDate()).orElse(Duration.ZERO
    );

    employeecontractService.updateOvertimeStatic(employeecontractId, newOvertimeStatic);
  }

  @EventListener
  void onTimereportsDeleted(TimereportsDeletedEvent event) {
    // collect all employee contracts in a map to have better access later
    var contracts = event.getIds().stream()
        .map(TimereportDeleteId::getEmployeeorderId)
        .distinct()
        .map(employeeorderService::getEmployeeorderById)
        .map(eo -> Pair.of(eo.getId(), eo.getEmployeecontract()))
        .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
    // what employee contracts need to recalculate - only when there are changed time reports before the acceptance date
    // (this is to reduce too many calculations)
    var contractsToRecalculate = event.getIds().stream()
        .filter(id -> {
          var contract = contracts.get(id.getEmployeeorderId());
          if(contract.getReportAcceptanceDate() == null) return false;
          return !id.getReferenceDay().isAfter(contract.getReportAcceptanceDate());
        })
        .map(TimereportDeleteId::getEmployeeorderId)
        .distinct()
        .map(contracts::get)
        .map(Employeecontract::getId)
        .distinct()
        .toList();
    recomputeOvertime(contractsToRecalculate);
  }

  @EventListener
  void onTimereportsCreatedOrUpdated(TimereportsCreatedOrUpdatedEvent event) {
    var timereports = event.getIds().stream()
        .map(timereportService::getTimereportById)
        .toList();
    // collect all employee contracts in a map to have better access later
    var contracts = timereports.stream()
        .map(TimereportDTO::getEmployeecontractId)
        .distinct()
        .map(employeecontractService::getEmployeecontractById)
        .collect(Collectors.toMap(AuditedEntity::getId, identity()));
    // what employee contracts need to recalculate - only when there are changed time reports before the acceptance date
    // (this is to reduce too many calculations)
    var contractsToRecalculate = timereports.stream()
        .filter(t -> {
          var contract = contracts.get(t.getEmployeecontractId());
          if(contract.getReportAcceptanceDate() == null) return false;
          return !t.getReferenceday().isAfter(contract.getReportAcceptanceDate());
        })
        .map(TimereportDTO::getEmployeecontractId)
        .distinct()
        .toList();
    recomputeOvertime(contractsToRecalculate);
  }

  @EventListener
  void onEmployeecontractUpdate(EmployeecontractUpdateEvent event) {
    var employeecontract = event.getDomainObject();
    recomputeOvertime(List.of(employeecontract.getId()));
  }

  private void recomputeOvertime(List<Long> employeecontractIds) {
    employeecontractIds.forEach(this::updateOvertimeStatic);
  }

  @Getter
  @RequiredArgsConstructor
  private class OvertimeInfo {
    private final Duration actual;
    private final Duration adjustment;
    private final Duration sum;
    private final Duration target;
    private final Duration diff;
  }

}