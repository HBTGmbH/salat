package org.tb.employee.viewhelper;

import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;
import static org.tb.common.util.DateUtils.today;
import static org.tb.employee.controller.EmployeeUiStateKeyContributor.EMPLOYEE_CONTRACT_ID;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.tb.common.domain.AuditedEntity;
import org.tb.common.web.UiState;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;

@Component
@Scope(value = SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class EmployeeContractSelectorViewHelper {

    private final EmployeecontractService employeecontractService;
    private final UiState uiState;
    private final AuthorizedEmployee authorizedEmployee;

    private List<Employeecontract> cachedContracts;

    public List<Employeecontract> getViewableContracts() {
        if (cachedContracts == null) {
            cachedContracts = employeecontractService.getViewableEmployeeContractsForAuthorizedUserValidAt(today());
        }
        return cachedContracts;
    }

    public boolean isVisible() {
        return getViewableContracts().size() > 1;
    }

    public Long getSelectedContractId() {
        var id = uiState.getLongValue(EMPLOYEE_CONTRACT_ID);
        return id != null ? id : employeecontractService.getCurrentContract(authorizedEmployee.getEmployeeId())
                .map(AuditedEntity::getId)
                .orElse(null);
    }

}
