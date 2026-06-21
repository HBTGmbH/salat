package org.tb.employee.controller;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.tb.common.web.UiStateKeyContributor;

@Component
public class EmployeeUiStateKeyContributor implements UiStateKeyContributor {

    public static final String SELECTED_CONTRACT = "selectedContract";

    @Override
    public Map<String, String> getParamToKeyMappings() {
        return Map.of("employeeContractId", SELECTED_CONTRACT);
    }
}
