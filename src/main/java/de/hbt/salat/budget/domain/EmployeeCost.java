package de.hbt.salat.budget.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import de.hbt.salat.common.domain.AuditedEntity;

/**
 * One rate period of a cost category. Several of them share a category to model a rate that changed
 * over time; they must not overlap (#1208), and the key over category and start keeps two periods
 * from starting on the same day.
 */
@Entity
@Table(name = "employee_cost", uniqueConstraints = @UniqueConstraint(name = "uk_employee_cost_category_valid_from",
    columnNames = {"category_id", "valid_from"}))
@Getter
@Setter
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class EmployeeCost extends AuditedEntity {

    /** The category, by foreign key (#1209). */
    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id", nullable = false, foreignKey = @ForeignKey(name = "fk_employee_cost_category"))
    private CostCategory category;

    @Column(name = "cost_cents_per_hour", nullable = false)
    private Integer costCentsPerHour;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    /** The name of the category. */
    public String getName() {
        return category.getName();
    }

}
