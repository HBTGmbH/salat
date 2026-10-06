package de.hbt.salat.budget.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;

/**
 * The cost category that applies to the work of a person — for one suborder, for a whole customer order,
 * or in general (#1343). The resolution takes the narrowest assignment valid on the day: suborder, then
 * customer order, then the general one ({@link EmployeeCostLookup}). A standby order is no exception; it
 * costs what the assignments say, and a separate pay for standby is an assignment to its suborder or
 * order.
 *
 * <p>Suborder and customer order exclude each other: an assignment to an order already covers all its
 * suborders.
 */
@Entity
@Table(name = "employee_cost_employee")
@Getter
@Setter
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class EmployeeCostAssignment extends AuditedEntity {

    /** The cost category, by foreign key (#1209). */
    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_cost_category_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_employee_cost_employee_category"))
    private CostCategory category;

    /**
     * The person the cost applies to (#968).
     *
     * <p>A reference to master data of another module (#1367, ADR-0036): read only, no cascade.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /**
     * The customer order the assignment is specific to (#1343): it covers every suborder of the order,
     * also one created later. {@code null} for an assignment to a suborder and for a general one.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customerorder_id")
    private Customerorder customerorder;

    /**
     * The suborder the assignment is specific to (#1205). {@code null} for an assignment to a customer
     * order and for a general one.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suborder_id")
    private Suborder suborder;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    /** The name of the category. */
    public String getEmployeeCostName() {
        return category.getName();
    }

    /** Whether the assignment is specific to a suborder. */
    public boolean isSuborderSpecific() {
        return suborder != null;
    }

    /** Whether the assignment is specific to a whole customer order (#1343). */
    public boolean isCustomerorderSpecific() {
        return customerorder != null;
    }

    /** The id of {@link #employee}, read off the reference without loading the person. */
    public Long getEmployeeId() {
        return employee != null ? employee.getId() : null;
    }

    /** The id of {@link #customerorder}, read off the reference without loading the order. */
    public Long getCustomerorderId() {
        return customerorder != null ? customerorder.getId() : null;
    }

    /** The id of {@link #suborder}, read off the reference without loading the suborder. */
    public Long getSuborderId() {
        return suborder != null ? suborder.getId() : null;
    }

}
