package org.tb.statistic.service;

import static java.time.Duration.ofMinutes;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.common.domain.AuditedEntity;
import org.tb.common.util.DurationUtils;
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

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class StatisticService {

  private final TimereportService timereportService;
  private final EmployeeorderService employeeorderService;
  private final SuborderService suborderService;
  private final CustomerorderService customerorderService;

  @EventListener
  void onTimereportsCreatedOrUpdated(TimereportsCreatedOrUpdatedEvent event) {
    var employeeorders = event.getIds().stream()
        .map(timereportService::getTimereportById)
        .map(TimereportDTO::getEmployeeorderId)
        .distinct()
        .map(employeeorderService::getEmployeeorderById)
        .toList();
    generateTimereportStatistics(employeeorders);
  }

  @EventListener
  void onTimereportsDeleted(TimereportsDeletedEvent event) {
    var employeeorders = event.getIds().stream()
        .map(TimereportDeleteId::getEmployeeorderId)
        .distinct()
        .map(employeeorderService::getEmployeeorderById)
        .toList();
    generateTimereportStatistics(employeeorders);
  }

  private void generateTimereportStatistics(List<Employeeorder> employeeorders) {
    employeeorders.forEach(eo -> {
      var duration = timereportService.getTotalDurationMinutesForEmployeeOrder(eo.getId());
      log.info("EO: Duration for {} of {}: {}",
          eo.getSuborder().getCompleteOrderSign(),
          eo.getEmployeecontract().getEmployee().getSign(),
          DurationUtils.format(ofMinutes(duration))
      );
    });
    var suborders = employeeorders.stream()
        .map(Employeeorder::getSuborder)
        .flatMap(s -> s.withParents().stream())
        .map(AuditedEntity::getId)
        .distinct()
        .map(suborderService::getSuborderById)
        .toList();
    suborders.forEach(so -> {
      var duration = timereportService.getTotalDurationMinutesForSuborders(List.of(so.getId()));
      log.info("SO Duration for {}: {}",
          so.getCompleteOrderSign(),
          DurationUtils.format(ofMinutes(duration))
      );
    });
    var customerorders = suborders.stream()
        .map(Suborder::getCustomerorder)
        .map(AuditedEntity::getId)
        .distinct()
        .map(customerorderService::getCustomerorderById)
        .toList();
    customerorders.forEach(co -> {
      var duration = timereportService.getTotalDurationMinutesForCustomerOrder(co.getId());
      log.info("CO Duration for {}: {}",
          co.getSign(),
          DurationUtils.format(ofMinutes(duration))
      );
    });
  }

}
