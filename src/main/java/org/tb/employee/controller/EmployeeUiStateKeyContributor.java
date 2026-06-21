package org.tb.employee.controller;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.tb.common.web.UiStateKey;
import org.tb.common.web.UiStateKeyContributor;

@Component
public class EmployeeUiStateKeyContributor implements UiStateKeyContributor {

    public static final UiStateKey SELECTED_CONTRACT = new UiStateKey("selectedContract");

    @Override
    public Map<String, UiStateKey> getParamToKeyMappings() {
        return Map.of("employeeContractId", SELECTED_CONTRACT);
    }
}
