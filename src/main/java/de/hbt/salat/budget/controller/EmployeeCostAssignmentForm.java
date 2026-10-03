package de.hbt.salat.budget.controller;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public class EmployeeCostAssignmentForm {

    private Long id;
    private String employeeCostName;
    private Long employeeId;
    /** The suborder, by id (#1205); {@code null} for the general assignment of the person. */
    private Long suborderId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate validFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate validUntil;

    public boolean isNew() {
        return id == null;
    }

}
