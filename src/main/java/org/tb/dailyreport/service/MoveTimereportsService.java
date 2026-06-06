package org.tb.dailyreport.service;

import static org.tb.common.exception.ErrorCode.TR_MOVE_DATE_RANGE_OUTSIDE_TARGET;
import static org.tb.common.exception.ErrorCode.TR_MOVE_SOURCE_TARGET_SAME;
import static org.tb.common.exception.ErrorCode.TR_TIME_REPORT_NOT_FOUND;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.common.exception.InvalidDataException;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.TimereportRepository;
import org.tb.dailyreport.service.MoveTimereportsPreview.NewEmployeeorderInfo;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.EmployeeorderDAO;
import org.tb.order.persistence.SuborderDAO;
import org.tb.order.service.EmployeeorderService;

@Service
@RequiredArgsConstructor
@Transactional
@Authorized
public class MoveTimereportsService {

  private final TimereportDAO timereportDAO;
  private final TimereportRepository timereportRepository;
  private final SuborderDAO suborderDAO;
  private final EmployeeorderDAO employeeorderDAO;
  private final EmployeeorderService employeeorderService;
  private final EmployeecontractService employeecontractService;

  @Transactional(readOnly = true)
  @Authorized(requiresManager = true)
  public MoveTimereportsPreview preview(
      long sourceSuborderId, long targetSuborderId,
      List<Long> employeeContractIds, LocalDate fromDate, LocalDate toDate) {

    var sourceSuborder = getSuborder(sourceSuborderId);
    var targetSuborder = getSuborder(targetSuborderId);
    validate(sourceSuborderId, targetSuborderId, targetSuborder, fromDate, toDate);

    var timereports = loadTimereports(sourceSuborderId, employeeContractIds, fromDate, toDate);
    var newOrders = computeNewEmployeeOrders(timereports, targetSuborderId, targetSuborder);

    return new MoveTimereportsPreview(timereports, newOrders, sourceSuborder, targetSuborder, fromDate, toDate);
  }

  @Authorized(requiresManager = true)
  public void move(
      long sourceSuborderId, long targetSuborderId,
      List<Long> employeeContractIds, LocalDate fromDate, LocalDate toDate) {

    var targetSuborder = getSuborder(targetSuborderId);
    validate(sourceSuborderId, targetSuborderId, targetSuborder, fromDate, toDate);

    var timereports = loadTimereports(sourceSuborderId, employeeContractIds, fromDate, toDate);

    // Build or find employee orders on target suborder, keyed by employeeContractId
    Map<Long, Employeeorder> employeeOrders = new LinkedHashMap<>();
    for (var ecId : uniqueContractIds(timereports)) {
      var existing = employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndSuborderId(ecId, targetSuborderId);
      if (!existing.isEmpty()) {
        employeeOrders.put(ecId, existing.get(0));
      } else {
        var earliestDate = earliestDateFor(timereports, ecId);
        var newEo = buildEmployeeOrder(ecId, targetSuborder, earliestDate);
        employeeorderService.create(newEo);
        employeeOrders.put(ecId, newEo);
      }
    }

    // Reassign each timereport to the target suborder and its employee order
    for (var dto : timereports) {
      var tr = timereportRepository.findById(dto.getId())
          .orElseThrow(() -> new InvalidDataException(TR_TIME_REPORT_NOT_FOUND, dto.getId()));
      tr.setSuborder(targetSuborder);
      tr.setEmployeeorder(employeeOrders.get(dto.getEmployeecontractId()));
    }
  }

  private void validate(long sourceSuborderId, long targetSuborderId,
      Suborder targetSuborder, LocalDate fromDate, LocalDate toDate) {
    if (sourceSuborderId == targetSuborderId) {
      throw new InvalidDataException(TR_MOVE_SOURCE_TARGET_SAME);
    }
    var targetFrom = targetSuborder.getFromDate();
    var targetUntil = targetSuborder.getUntilDate();
    boolean outsideFrom = targetFrom != null && fromDate.isBefore(targetFrom);
    boolean outsideUntil = targetUntil != null && toDate.isAfter(targetUntil);
    if (outsideFrom || outsideUntil) {
      throw new InvalidDataException(TR_MOVE_DATE_RANGE_OUTSIDE_TARGET);
    }
  }

  private List<TimereportDTO> loadTimereports(long sourceSuborderId,
      List<Long> employeeContractIds, LocalDate fromDate, LocalDate toDate) {
    var all = timereportDAO.getTimereportsByDatesAndSuborderId(fromDate, toDate, sourceSuborderId);
    if (!employeeContractIds.isEmpty()) {
      all = all.stream()
          .filter(dto -> employeeContractIds.contains(dto.getEmployeecontractId()))
          .toList();
    }
    return all.stream()
        .sorted(Comparator.comparing(TimereportDTO::getEmployeeName)
            .thenComparing(TimereportDTO::getReferenceday))
        .toList();
  }

  private List<NewEmployeeorderInfo> computeNewEmployeeOrders(
      List<TimereportDTO> timereports, long targetSuborderId, Suborder targetSuborder) {
    var result = new ArrayList<NewEmployeeorderInfo>();
    for (var ecId : uniqueContractIds(timereports)) {
      var existing = employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndSuborderId(ecId, targetSuborderId);
      if (existing.isEmpty()) {
        var name = timereports.stream()
            .filter(dto -> dto.getEmployeecontractId() == ecId)
            .map(TimereportDTO::getEmployeeName)
            .findFirst().orElse("");
        result.add(new NewEmployeeorderInfo(name, earliestDateFor(timereports, ecId), targetSuborder.getUntilDate()));
      }
    }
    return result;
  }

  private List<Long> uniqueContractIds(List<TimereportDTO> timereports) {
    return timereports.stream()
        .map(TimereportDTO::getEmployeecontractId)
        .distinct()
        .toList();
  }

  private LocalDate earliestDateFor(List<TimereportDTO> timereports, long ecId) {
    return timereports.stream()
        .filter(dto -> dto.getEmployeecontractId() == ecId)
        .map(TimereportDTO::getReferenceday)
        .min(Comparator.naturalOrder())
        .orElseThrow();
  }

  private Employeeorder buildEmployeeOrder(long ecId, Suborder targetSuborder, LocalDate fromDate) {
    var eo = new Employeeorder();
    eo.setFromDate(fromDate);
    eo.setUntilDate(targetSuborder.getUntilDate());
    eo.setSuborder(targetSuborder);
    eo.setEmployeecontract(employeecontractService.getEmployeecontractById(ecId));
    eo.setSign(" ");
    eo.setDebithours(Duration.ZERO);
    return eo;
  }

  private Suborder getSuborder(long suborderId) {
    var so = suborderDAO.getSuborderById(suborderId);
    if (so == null) {
      throw new InvalidDataException(TR_TIME_REPORT_NOT_FOUND, suborderId);
    }
    return so;
  }
}
