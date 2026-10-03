package de.hbt.salat.budget.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import de.hbt.salat.common.domain.AuditedEntity;

@Entity
@Table(name = "employee_cost_employee")
@Getter
@Setter
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class EmployeeCostAssignment extends AuditedEntity {

    @Column(name = "employee_cost_name", nullable = false)
    private String employeeCostName;

    /**
     * The person the cost applies to (#968). {@code null} only on a row the migration could not
     * resolve: its sign was carried by no person or by several. Such a row resolves to nobody and is
     * marked on the category page until somebody picks the person.
     */
    @Column(name = "employee_id")
    private Long employeeId;

    /**
     * The sign of {@link #employeeId}, kept only because views, ETL definitions and reports still
     * join on it (#968). The application no longer reads it: it is written on save from the chosen
     * person and follows a sign change ({@code EmployeeSignChangedListener}), and it goes away once
     * those readers have moved to {@code employee_id}.
     */
    @Column(name = "employee_sign", nullable = false)
    private String employeeSign;

    /**
     * The suborder the assignment is specific to (#1205). {@code null} means the assignment applies
     * regardless of suborder.
     */
    @Column(name = "suborder_id")
    private Long suborderId;

    /**
     * The complete order sign of {@link #suborderId} ({@code Suborder#getCompleteOrderSign()},
     * e.g. {@code ORDER/01/02}), kept because views, ETL definitions and reports read it, and
     * written from the suborder ({@code OrderSignMirrorListener}); the application never reads it (#1212).
     */
    @Column(name = "suborder_sign")
    private String suborderSign;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    /** Whether the person could not be resolved by the migration (→ {@link #employeeId}). */
    public boolean isEmployeeUnresolved() {
        return employeeId == null;
    }

    /** Whether the assignment is specific to a suborder rather than general. */
    public boolean isSuborderSpecific() {
        return suborderId != null;
    }

}
