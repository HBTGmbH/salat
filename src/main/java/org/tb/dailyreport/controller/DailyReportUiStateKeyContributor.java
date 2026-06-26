package org.tb.dailyreport.controller;

import org.springframework.stereotype.Component;
import org.tb.common.web.UiStateKey;
import org.tb.common.web.UiStateKeyContributor;

import java.util.Map;

import static java.util.Map.of;

@Component
public class DailyReportUiStateKeyContributor implements UiStateKeyContributor {

    public static final UiStateKey EMPLOYEE_CONTRACT_ID = new UiStateKey("employeeContract.Id");

    @Override
    public Map<String, UiStateKey> getParamToKeyMappings() {
        return of(
            "employeeContractId", EMPLOYEE_CONTRACT_ID
        );
    }
}
