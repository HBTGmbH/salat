package de.hbt.salat.dailyreport.service;

import static de.hbt.salat.common.exception.ErrorCode.SO_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.TR_MOVE_ACCEPTED_REQ_ADMIN;
import static de.hbt.salat.common.exception.ErrorCode.TR_MOVE_DATE_RANGE_OUTSIDE_TARGET;
import static de.hbt.salat.common.exception.ErrorCode.TR_MOVE_SOURCE_TARGET_SAME;

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
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.persistence.TimereportDAO;
import de.hbt.salat.dailyreport.service.MoveTimereportsPreview.NewEmployeeorderInfo;
import de.hbt.salat.order.domain.NewEmployeeorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.persistence.EmployeeorderDAO;
import de.hbt.salat.order.persistence.SuborderDAO;
import de.hbt.salat.order.service.EmployeeorderService;

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
  private final AuthorizedUser authorizedUser;

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

    return new MoveTimereportsPreview(timereports, newOrders, sourceSuborder, targetSuborder, fromDate, toDate,
        mayMove(timereports));
  }

  public void move(
      long sourceSuborderId, long targetSuborderId,
      List<Long> employeeContractIds, LocalDate fromDate, LocalDate toDate) {

    var targetSuborder = getSuborder(targetSuborderId);
    validate(sourceSuborderId, targetSuborderId, targetSuborder, fromDate, toDate);

    var timereports = loadTimereports(sourceSuborderId, employeeContractIds, fromDate, toDate);
    if (!mayMove(timereports)) {
      throw new AuthorizationException(TR_MOVE_ACCEPTED_REQ_ADMIN);
    }
    var byContract = groupByContract(timereports);

    // Find or create the employee order on the target suborder, its id keyed by employeeContractId;
    // a new one the order module creates from values (#1459)
    Map<Long, Long> employeeOrderIds = new LinkedHashMap<>();
    for (var entry : byContract.entrySet()) {
      var ecId = entry.getKey();
      var existing = employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndSuborderId(ecId, targetSuborderId);
      if (!existing.isEmpty()) {
        employeeOrderIds.put(ecId, existing.get(0).getId());
      } else {
        employeeOrderIds.put(ecId, employeeorderService.createEmployeeorder(
            new NewEmployeeorder(ecId, targetSuborderId, earliestDateFor(entry.getValue()))));
      }
    }

    for (var dto : timereports) {
      timereportService.updateTimereport(
          dto.getId(),
          dto.getEmployeecontractId(),
          employeeOrderIds.get(dto.getEmployeecontractId()),
          dto.getReferenceday(),
          dto.getTaskdescription(),
          dto.isTraining(),
          dto.getDuration(),
          true);
    }
  }

  /**
   * The move is forced past the status check of each booking, which is what lets a manager move
   * released bookings of everybody. Accepted ones only an admin changes (#1164), so the move asks
   * that itself — before it creates a single employee order.
   */
  private boolean mayMove(List<TimereportDTO> timereports) {
    return timereports.stream().noneMatch(MoveTimereportsPreview::isAccepted) || authorizedUser.isAdmin();
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

  private Suborder getSuborder(long suborderId) {
    var so = suborderDAO.getSuborderById(suborderId);
    if (so == null) {
      throw new InvalidDataException(SO_NOT_FOUND, suborderId);
    }
    return so;
  }
}
