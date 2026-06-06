package org.tb.dailyreport.service;

import static org.tb.common.exception.ErrorCode.SO_NOT_FOUND;
import static org.tb.common.exception.ErrorCode.TR_MOVE_DATE_RANGE_OUTSIDE_TARGET;
import static org.tb.common.exception.ErrorCode.TR_MOVE_SOURCE_TARGET_SAME;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.common.LocalDateRange;
import org.tb.common.exception.InvalidDataException;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.persistence.TimereportDAO;
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
@Authorized(requiresManager = true)
public class MoveTimereportsService {

  private final TimereportDAO timereportDAO;
  private final TimereportService timereportService;
  private final SuborderDAO suborderDAO;
  private final EmployeeorderDAO employeeorderDAO;
  private final EmployeeorderService employeeorderService;
  private final EmployeecontractService employeecontractService;

  @Transactional(readOnly = true)
  public MoveTimereportsPreview preview(
      long sourceSuborderId, long targetSuborderId,
      List<Long> employeeContractIds, LocalDate fromDate, LocalDate toDate) {

    var sourceSuborder = getSuborder(sourceSuborderId);
    var targetSuborder = getSuborder(targetSuborderId);
    validate(sourceSuborderId, targetSuborderId, targetSuborder, fromDate, toDate);

    var timereports = loadTimereports(sourceSuborderId, employeeContractIds, fromDate, toDate);
    var byContract = groupByContract(timereports);
    var newOrders = computeNewEmployeeOrders(byContract, targetSuborderId, targetSuborder);

    return new MoveTimereportsPreview(timereports, newOrders, sourceSuborder, targetSuborder, fromDate, toDate);
  }

  public void move(
      long sourceSuborderId, long targetSuborderId,
      List<Long> employeeContractIds, LocalDate fromDate, LocalDate toDate) {

    var targetSuborder = getSuborder(targetSuborderId);
    validate(sourceSuborderId, targetSuborderId, targetSuborder, fromDate, toDate);

    var timereports = loadTimereports(sourceSuborderId, employeeContractIds, fromDate, toDate);
    var byContract = groupByContract(timereports);

    // Build or find employee orders on target suborder, keyed by employeeContractId
    Map<Long, Employeeorder> employeeOrders = new LinkedHashMap<>();
    for (var entry : byContract.entrySet()) {
      var ecId = entry.getKey();
      var existing = employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndSuborderId(ecId, targetSuborderId);
      if (!existing.isEmpty()) {
        employeeOrders.put(ecId, existing.get(0));
      } else {
        var newEo = buildEmployeeOrder(ecId, targetSuborder, earliestDateFor(entry.getValue()));
        employeeorderService.create(newEo);
        employeeOrders.put(ecId, newEo);
      }
    }

    for (var dto : timereports) {
      timereportService.updateTimereport(
          dto.getId(),
          dto.getEmployeecontractId(),
          employeeOrders.get(dto.getEmployeecontractId()).getId(),
          dto.getReferenceday(),
          dto.getTaskdescription(),
          dto.isTraining(),
          dto.getDurationhours(),
          dto.getDurationminutes(),
          true);
    }
  }

  private void validate(long sourceSuborderId, long targetSuborderId,
      Suborder targetSuborder, LocalDate fromDate, LocalDate toDate) {
    if (sourceSuborderId == targetSuborderId) {
      throw new InvalidDataException(TR_MOVE_SOURCE_TARGET_SAME);
    }
    if (!targetSuborder.getValidity().contains(new LocalDateRange(fromDate, toDate))) {
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

  private Map<Long, List<TimereportDTO>> groupByContract(List<TimereportDTO> timereports) {
    return timereports.stream()
        .collect(Collectors.groupingBy(TimereportDTO::getEmployeecontractId,
            LinkedHashMap::new, Collectors.toList()));
  }

  private List<NewEmployeeorderInfo> computeNewEmployeeOrders(
      Map<Long, List<TimereportDTO>> byContract, long targetSuborderId, Suborder targetSuborder) {
    var result = new ArrayList<NewEmployeeorderInfo>();
    for (var entry : byContract.entrySet()) {
      var ecId = entry.getKey();
      var existing = employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndSuborderId(ecId, targetSuborderId);
      if (existing.isEmpty()) {
        var name = entry.getValue().get(0).getEmployeeName();
        result.add(new NewEmployeeorderInfo(name, earliestDateFor(entry.getValue()), targetSuborder.getUntilDate()));
      }
    }
    return result;
  }

  private LocalDate earliestDateFor(List<TimereportDTO> dtos) {
    return dtos.stream()
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
      throw new InvalidDataException(SO_NOT_FOUND, suborderId);
    }
    return so;
  }
}
