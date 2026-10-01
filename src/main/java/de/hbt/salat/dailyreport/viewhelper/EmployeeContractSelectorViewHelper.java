package de.hbt.salat.dailyreport.viewhelper;

import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;
import static de.hbt.salat.dailyreport.controller.DailyReportUiStateKeyContributor.EMPLOYEE_CONTRACT_ID;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.common.web.UiState;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeecontractService;

@Component
@Scope(value = SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
@Authorized
public class EmployeeContractSelectorViewHelper {

    private final EmployeecontractService employeecontractService;
    private final UiState uiState;
    private final AuthorizedEmployee authorizedEmployee;

    private List<Employeecontract> cachedContracts;
    private Long cachedCurrentContractId;
    private boolean currentContractIdResolved;

    public List<Employeecontract> getViewableContracts() {
        if (cachedContracts == null) {
            cachedContracts = employeecontractService.getVisibleEmployeeContractsForAuthorizedUser();
        }
        return cachedContracts;
    }

    public boolean isVisible() {
        return getViewableContracts().size() > 1;
    }

    /**
     * Ob die Auswahl auf dieser Seite wirkt: im Bereich Buchungen, außer in der Abnahme, die ihre
     * Personen selbst wählt. Selektor und Vertragsbalken in {@code layout/base.html} fragen beide hier.
     */
    public boolean appliesTo(String section, String subSection) {
        return "dailyreport".equals(section) && !"acceptance".equals(subSection) && isVisible();
    }

    public Long getSelectedContractId() {
        var id = uiState.getLongValue(EMPLOYEE_CONTRACT_ID);
        if (id != null) return id;
        // Nur der DB-Fallback wird gepuffert; UiState wird weiterhin bei jedem Aufruf gelesen,
        // damit eine Änderung während des Requests weiterhin Vorrang behält. Das Template ruft
        // diese Methode pro Render mehrfach auf.
        if (!currentContractIdResolved) {
            cachedCurrentContractId = employeecontractService.getCurrentContract(authorizedEmployee.getEmployeeId())
                    .map(AuditedEntity::getId)
                    .orElse(null);
            currentContractIdResolved = true;
        }
        return cachedCurrentContractId;
    }

    public Employeecontract getSelectedContract() {
        var id = getSelectedContractId();
        if (id == null) return null;
        return getViewableContracts().stream()
                .filter(ec -> ec.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public boolean isMultiContractEmployee() {
        return !getOtherContractsOfSelectedEmployee().isEmpty();
    }

    /**
     * Die übrigen Verträge der gewählten Person, die der Selektor auch anbietet — der Balken über
     * der Kopfzeile (#1231) bietet sie als Wechsel in einen anderen Zeitraum an. Leer, wenn nichts
     * gewählt ist oder die Person nur diesen einen Vertrag hat.
     */
    public List<Employeecontract> getOtherContractsOfSelectedEmployee() {
        var selected = getSelectedContract();
        if (selected == null) return List.of();
        long empId = selected.getEmployee().getId();
        return getViewableContracts().stream()
                .filter(ec -> ec.getEmployee().getId() == empId)
                .filter(ec -> !ec.getId().equals(selected.getId()))
                .toList();
    }

}
