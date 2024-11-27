package org.tb.statistic.service;

import static java.time.Duration.ofMinutes;
import static java.time.LocalDateTime.now;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.common.domain.AuditedEntity;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.event.TimereportsCreatedOrUpdatedEvent;
import org.tb.dailyreport.event.TimereportsDeletedEvent;
import org.tb.dailyreport.event.TimereportsDeletedEvent.TimereportDeleteId;
import org.tb.dailyreport.service.TimereportService;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.EmployeeorderService;
import org.tb.order.service.SuborderService;
import org.tb.statistic.domain.StatisticValue;
import org.tb.statistic.persistence.StatisticValueRepository;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class StatisticService {

  private final TimereportService timereportService;
  private final EmployeeorderService employeeorderService;
  private final SuborderService suborderService;
  private final CustomerorderService customerorderService;
  private final StatisticValueRepository statisticValueRepository;

  @Async
  @EventListener
  void onTimereportsCreatedOrUpdated(TimereportsCreatedOrUpdatedEvent event) {
    var timereports = event.getIds().stream()
        .map(timereportService::getTimereportById)
        .filter(Objects::nonNull) // because of async, object may no longer be available
        .toList();
    var minDate = timereports.stream()
        .map(TimereportDTO::getReferenceday)
        .min(LocalDate::compareTo)
        .orElse(LocalDate.now());
    var maxDate = timereports.stream()
        .map(TimereportDTO::getReferenceday)
        .max(LocalDate::compareTo)
        .orElse(LocalDate.now());

    var employeeorders = timereports.stream()
        .map(TimereportDTO::getEmployeeorderId)
        .filter(Objects::nonNull) // because of async, object may no longer be available
        .distinct()
        .map(employeeorderService::getEmployeeorderById)
        .toList();
    
    var comment = "time report(s) created or updated @ %s".formatted(now());

    generateOrderStatistics(employeeorders, minDate, maxDate, comment);
  }

  @Async
  @EventListener
  void onTimereportsDeleted(TimereportsDeletedEvent event) {
    var referencedDays = event.getIds().stream()
        .map(TimereportDeleteId::getReferenceDay)
        .distinct()
        .toList();
    var minDate = referencedDays.stream()
        .min(LocalDate::compareTo)
        .orElse(LocalDate.now());
    var maxDate = referencedDays.stream()
        .max(LocalDate::compareTo)
        .orElse(LocalDate.now());

    var employeeorders = event.getIds().stream()
        .map(TimereportDeleteId::getEmployeeorderId)
        .distinct()
        .map(employeeorderService::getEmployeeorderById)
        .filter(Objects::nonNull) // because of async, object may no longer be available
        .toList();

    var comment = "time report(s) deleted @ %s".formatted(now());

    generateOrderStatistics(employeeorders, minDate, maxDate, comment);
  }

  private void generateOrderStatistics(List<Employeeorder> employeeorders, LocalDate min, LocalDate max, String comment) {
    employeeorders.forEach(eo -> {
      var duration = timereportService.getTotalDurationMinutesForEmployeeOrder(eo.getId());
      updateStatistic("EMPLOYEEORDER", "timereport.duration.total", eo.getId(), ofMinutes(duration), comment);
    });
    var suborders = employeeorders.stream()
        .map(Employeeorder::getSuborder)
        .flatMap(s -> s.withParents().stream())
        .map(AuditedEntity::getId)
        .distinct()
        .map(suborderService::getSuborderById)
        .toList();
    suborders.forEach(so -> {
      {
        var duration = timereportService.getTotalDurationMinutesForSuborders(List.of(so.getId()));
        updateStatistic("SUBORDER", "timereport.duration.total", so.getId(), ofMinutes(duration), comment);
      }
      var months = getMonths(min, max);
      months.forEach(month -> {
        var duration = timereportService.getTotalDurationMinutesForSuborder(so.getId(), month.atDay(1), month.atEndOfMonth());
        updateStatistic("SUBORDER", "timereport.duration." + month, so.getId(), ofMinutes(duration), comment);
      });
    });
    var customerorders = suborders.stream()
        .map(Suborder::getCustomerorder)
        .map(AuditedEntity::getId)
        .distinct()
        .map(customerorderService::getCustomerorderById)
        .toList();
    customerorders.forEach(co -> {
      var duration = timereportService.getTotalDurationMinutesForCustomerOrder(co.getId());
      updateStatistic("CUSTOMERORDER", "timereport.duration.total", co.getId(), ofMinutes(duration), comment);
    });
  }

  private List<YearMonth> getMonths(LocalDate min, LocalDate max) {
    List<YearMonth> result = new LinkedList<>();

    YearMonth current = YearMonth.from(min);
    YearMonth end = YearMonth.from(max);

    while (!current.isAfter(end)) {
      result.add(current);
      current = current.plusMonths(1);
    }

    return result;
  }

  private void updateStatistic(String category, String key, long objectId, Duration value, String comment) {
    var statisticValue = statisticValueRepository
        .findByCategoryAndKeyAndObjectId(category, key, objectId)
        .orElseGet(() -> new StatisticValue(category, key, objectId, value, comment));
    statisticValue.setValue(value);
    statisticValue.setComment(comment);
    statisticValueRepository.save(statisticValue);
  }

}
