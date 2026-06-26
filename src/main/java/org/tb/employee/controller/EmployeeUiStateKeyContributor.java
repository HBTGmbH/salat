package org.tb.employee.controller;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.tb.common.web.UiStateKey;
import org.tb.common.web.UiStateKeyContributor;

import static java.util.Map.of;

@Component
public class EmployeeUiStateKeyContributor implements UiStateKeyContributor {

    public static final UiStateKey EMPLOYEE_CONTRACT_SHOW_INVALID = new UiStateKey("employeeContract.ShowInvalid");
    public static final UiStateKey EMPLOYEE_CONTRACT_SHOW_HIDDEN = new UiStateKey("employeeContract.ShowHidden");
    public static final UiStateKey EMPLOYEE_CONTRACT_FILTER = new UiStateKey("employeeContract.Filter");
    public static final UiStateKey EMPLOYEE_ID = new UiStateKey("employee.Id");
    public static final UiStateKey EMPLOYEE_FILTER = new UiStateKey("employee.Filter");
    public static final UiStateKey EMPLOYEE_SHOW_HIDDEN = new UiStateKey("employee.ShowHidden");

    @Override
    public Map<String, UiStateKey> getParamToKeyMappings() {
        return of(
                "employeeId", EMPLOYEE_ID,
                "ecShowInvalid", EMPLOYEE_CONTRACT_SHOW_INVALID,
                "ecShowHidden", EMPLOYEE_CONTRACT_SHOW_HIDDEN,
                "ecFilter", EMPLOYEE_CONTRACT_FILTER,
                "eFilter", EMPLOYEE_FILTER,
                "eShowHidden", EMPLOYEE_SHOW_HIDDEN
        );
    }
}
