package de.hbt.salat.budget.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.common.domain.DurationMinutesConverter;
import de.hbt.salat.order.domain.Suborder;

/**
 * One line of the calculation behind a fixed price (#1404): so many hours on a suborder, worked by
 * people of one cost category.
 *
 * <p>The line covers its suborder and everything below it, like a plan does (→ {@link BudgetScope}).
 * Without a suborder it covers the whole plan, without a category it is for every person (#1496) — a
 * single line with neither calculates the order as a whole, whoever works the hours and wherever.
 * Which bookings count against it is decided by {@link FixedPriceCalculation}; the combination of
 * suborder and category is unique per plan. The service checks that: the unique constraint lets
 * {@code NULL} through more than once.
 */
@Entity
@Table(name = "order_budget_calculation",
    uniqueConstraints = @UniqueConstraint(name = "uk_order_budget_calculation_budget_suborder_category",
        columnNames = {"order_budget_id", "suborder_id", "cost_category_id"}))
@Getter
@Setter
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class OrderBudgetCalculation extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_budget_id", nullable = false)
    private OrderBudget orderBudget;

    /**
     * The suborder the hours were calculated for, {@code null} for the whole plan. A reference to
     * master data of another module (ADR-0036): read only, no cascade.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suborder_id")
    private Suborder suborder;

    /** The cost category the hours were calculated for, {@code null} for every person. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_category_id")
    private CostCategory category;

    @Column(name = "calculated_minutes", nullable = false)
    @Convert(converter = DurationMinutesConverter.class)
    private Duration calculatedHours;

    /** The id of {@link #suborder}, read off the reference without loading the suborder. */
    public Long getSuborderId() {
        return suborder != null ? suborder.getId() : null;
    }

    /** The id of {@link #category}, read off the reference without loading the category. */
    public Long getCategoryId() {
        return category != null ? category.getId() : null;
    }

}
