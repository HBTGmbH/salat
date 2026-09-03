package org.tb.dailyreport.controller;

import org.springframework.stereotype.Component;
import org.tb.common.web.UiStateKey;
import org.tb.common.web.UiStateKeyContributor;

import java.util.Map;

import static java.util.Map.of;

@Component
public class DailyReportUiStateKeyContributor implements UiStateKeyContributor {

    public static final UiStateKey EMPLOYEE_CONTRACT_ID = new UiStateKey("employeeContract.Id");
    public static final UiStateKey ACCEPTANCE_SUPERVISOR_ID = new UiStateKey("acceptance.Supervisor.Id");
    public static final UiStateKey ACCEPTANCE_EMPLOYEE_CONTRACT_ID = new UiStateKey("acceptance.EmployeeContract.Id");
    public static final UiStateKey MATRIX_YEAR = new UiStateKey("matrix.Year");
    public static final UiStateKey MATRIX_MONTH = new UiStateKey("matrix.Month");

    @Override
    public Map<String, UiStateKey> getParamToKeyMappings() {
        return of(
            "employeeContractId", EMPLOYEE_CONTRACT_ID,
            "accSupervisorId", ACCEPTANCE_SUPERVISOR_ID,
            "accEmployeeContractId", ACCEPTANCE_EMPLOYEE_CONTRACT_ID,
            "year", MATRIX_YEAR,
            "month", MATRIX_MONTH
        );
    }
}
