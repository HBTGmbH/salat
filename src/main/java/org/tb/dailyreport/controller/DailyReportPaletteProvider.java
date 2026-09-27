package org.tb.dailyreport.controller;

import static org.tb.common.palette.PaletteKind.PERSON;
import static org.tb.common.palette.PaletteKind.SUBORDER;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.stereotype.Component;
import org.tb.common.palette.PaletteHit;
import org.tb.common.palette.PaletteKind;
import org.tb.common.palette.PaletteLink;
import org.tb.common.palette.PaletteProvider;
import org.tb.common.palette.PaletteTarget;
import org.tb.common.palette.PaletteText;
import org.tb.common.util.DateUtils;
import org.tb.common.web.UiState;
import org.tb.dailyreport.auth.TimereportAuthorization;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.service.EmployeeorderService;

/**
 * The booking targets of objects the command palette found (#1157, ADR-0031): "Buchen auf …" for a
 * suborder, the daily and the matrix view for a person.
 *
 * <p><b>Buchen auf</b> is offered where the booking form would accept the booking today, for the
 * contract it would open with: the remembered selection if the user may read it, otherwise their own
 * current contract — the fallback of the dashboard (#1134). Three conditions, the same as the form
 * and its saving together: the contract is readable, it has an employee order on the suborder valid
 * today, and today is open for writing on it ({@link TimereportAuthorization#isWriteAllowedOn}, which
 * turns a people lead away from an open day of a team member). The link names the contract as the
 * form field {@code employeecontractId}, never as the filter — it must not change the selection
 * (ADR-0023).
 *
 * <p><b>Einzel- und Matrixübersicht</b> are offered for every contract the user may read. The views
 * check the same persons in {@code WorkingdayService} — manager, supervising people lead, owner —
 * plus holders of a working-day rule, who see fewer targets here than they could open: the safe
 * direction. Both links change the remembered selection, as the contract selector in the header does.
 */
@Component
@RequiredArgsConstructor
public class DailyReportPaletteProvider implements PaletteProvider {

  private final UiState uiState;
  private final AuthorizedEmployee authorizedEmployee;
  private final EmployeecontractService employeecontractService;
  private final EmployeeorderService employeeorderService;
  private final TimereportAuthorization timereportAuthorization;

  @Override
  public Map<String, List<PaletteTarget>> targetsFor(PaletteKind kind, List<PaletteHit> hits) {
    if (kind == SUBORDER) {
      return bookingTargets(hits);
    }
    if (kind == PERSON) {
      return viewTargets(hits);
    }
    return Map.of();
  }

  private Map<String, List<PaletteTarget>> bookingTargets(List<PaletteHit> hits) {
    var contract = selectedContract();
    var today = DateUtils.today();
    if (contract.isEmpty() || !timereportAuthorization.isWriteAllowedOn(contract.get(), today)) {
      return Map.of();
    }
    var ids = hits.stream().map(hit -> Long.valueOf(hit.key())).toList();
    var bookable = employeeorderService.getBookableSuborderIds(contract.get().getId(), ids, today);
    var targets = new HashMap<String, List<PaletteTarget>>();
    for (var hit : hits) {
      if (bookable.contains(Long.valueOf(hit.key()))) {
        targets.put(hit.key(), List.of(new PaletteTarget(PaletteText.of("main.palette.target.suborder.book", hit.title()),
            PaletteLink.to("/dailyreport/timereports/new")
                .param("suborderId", hit.key())
                .param("employeecontractId", contract.get().getId())
                .build(), 5)));
      }
    }
    return targets;
  }

  private Map<String, List<PaletteTarget>> viewTargets(List<PaletteHit> hits) {
    var targets = new HashMap<String, List<PaletteTarget>>();
    for (var hit : hits) {
      if (employeecontractService.getReadableEmployeecontract(Long.parseLong(hit.key())).isPresent()) {
        targets.put(hit.key(), List.of(
            new PaletteTarget(PaletteText.of("main.palette.target.person.daily"),
                PaletteLink.to("/dailyreport/daily").param("fEmployeeContractId", hit.key()).build(), 10),
            new PaletteTarget(PaletteText.of("main.palette.target.person.matrix"),
                PaletteLink.to("/dailyreport/matrix").param("fEmployeeContractId", hit.key()).build(), 11)));
      }
    }
    return targets;
  }

  private Optional<Employeecontract> selectedContract() {
    var selected = uiState.getLongValue(DailyReportUiStateKeyContributor.EMPLOYEE_CONTRACT_ID);
    if (selected != null && selected > 0) {
      var readable = employeecontractService.getReadableEmployeecontract(selected);
      if (readable.isPresent()) {
        return readable;
      }
    }
    var employeeId = authorizedEmployee.getEmployeeId();
    if (employeeId == null) {
      return Optional.empty();
    }
    try {
      return employeecontractService.getCurrentContract(employeeId);
    } catch (IncorrectResultSizeDataAccessException e) {
      // no running contract and more than one to come: the form would not know either
      return Optional.empty();
    }
  }
}
