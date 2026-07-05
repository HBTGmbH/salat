package org.tb.budget.controller;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public class EmployeeCostAssignmentForm {

    private Long id;
    private String employeeCostName;
    private String employeeSign;
    private String suborderSign;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate validFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate validUntil;

    public boolean isNew() {
        return id == null;
    }

}
