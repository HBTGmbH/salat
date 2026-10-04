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
    /** The customer order, by id (#1343); {@code null} unless the assignment is for a whole order. */
    private Long customerorderId;
    /** The suborder, by id (#1205); {@code null} unless the assignment is for a suborder. */
    private Long suborderId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate validFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate validUntil;

    public boolean isNew() {
        return id == null;
    }

}
