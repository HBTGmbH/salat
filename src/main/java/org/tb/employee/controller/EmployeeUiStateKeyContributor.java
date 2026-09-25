package org.tb.employee.controller;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.tb.common.web.UiStateKey;
import org.tb.common.web.UiStateKeyContributor;

import static java.util.Map.of;

@Component
public class EmployeeUiStateKeyContributor implements UiStateKeyContributor {

    public static final UiStateKey EMPLOYEE_CONTRACT_SHOW_INACTIVE = new UiStateKey("employeeContract.ShowInactive");
    public static final UiStateKey EMPLOYEE_CONTRACT_SHOW_HIDDEN = new UiStateKey("employeeContract.ShowHidden");
    public static final UiStateKey EMPLOYEE_CONTRACT_FILTER = new UiStateKey("employeeContract.Filter");
    public static final UiStateKey EMPLOYEE_ID = new UiStateKey("employee.Id");
    public static final UiStateKey EMPLOYEE_FILTER = new UiStateKey("employee.Filter");
    public static final UiStateKey EMPLOYEE_SHOW_HIDDEN = new UiStateKey("employee.ShowHidden");

    @Override
    public Map<String, UiStateKey> getParamToKeyMappings() {
        return of(
                "fEmployeeId", EMPLOYEE_ID,
                "fEmployeeContractShowInactive", EMPLOYEE_CONTRACT_SHOW_INACTIVE,
                "fEmployeeContractShowHidden", EMPLOYEE_CONTRACT_SHOW_HIDDEN,
                "fEmployeeContractFilter", EMPLOYEE_CONTRACT_FILTER,
                "fEmployeeFilter", EMPLOYEE_FILTER,
                "fEmployeeShowHidden", EMPLOYEE_SHOW_HIDDEN
        );
    }
}
