package org.tb.dailyreport;

import java.io.Serializable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.tb.employee.Employeecontract;

@Getter
@Setter
@RequiredArgsConstructor
public class TrainingOverview implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String year;
    private final Employeecontract employeecontract;
    private final String projectTrainingTime;
    private final String commonTrainingTime;
    private final String pTTHoursMin;
    private final String cTTHoursMin;

}
