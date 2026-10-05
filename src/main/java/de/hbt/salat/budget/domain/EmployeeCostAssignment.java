package de.hbt.salat.budget.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    /** The person the cost applies to (#968). */
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    /**
     * The customer order the assignment is specific to (#1343): it covers every suborder of the order,
     * also one created later. {@code null} for an assignment to a suborder and for a general one.
     */
    @Column(name = "customerorder_id")
    private Long customerorderId;

    /**
     * The suborder the assignment is specific to (#1205). {@code null} for an assignment to a customer
     * order and for a general one.
     */
    @Column(name = "suborder_id")
    private Long suborderId;

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
        return suborderId != null;
    }

    /** Whether the assignment is specific to a whole customer order (#1343). */
    public boolean isCustomerorderSpecific() {
        return customerorderId != null;
    }

}
