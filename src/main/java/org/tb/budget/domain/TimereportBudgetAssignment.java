package org.tb.budget.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.tb.common.domain.AuditedEntity;

/**
 * The explicit assignment of one time report to one budget plan. Only this stored relation counts —
 * a booking without an assignment belongs to no budget, there is no derived fallback (#908).
 */
@Entity
@Table(name = "timereport_order_budget")
@Getter
@Setter
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class TimereportBudgetAssignment extends AuditedEntity {

    /**
     * The id of the time report, deliberately not a {@code @ManyToOne} to it — although the column
     * does carry a database foreign key, and a relation in this direction
     * ({@code budget -> dailyreport}) would not close a module cycle. Two other reasons keep it an
     * id:
     * <ul>
     *   <li>The {@code Timereport} entity is reserved for {@code TimereportService}; everything
     *       else works on {@code TimereportDTO}, because time reports are sensitive data. That
     *       encapsulation is currently unbroken across the codebase.</li>
     *   <li>{@code Timereport} is soft-deleted ({@code @SQLDelete} + {@code @SQLRestriction}).
     *       Hibernate hides a deleted row, so a mapped relation would fail on access rather than
     *       read as gone — and because the row survives, the foreign key never cascades on a normal
     *       delete either. The cleanup therefore runs on {@code TimereportsDeletedEvent}; the
     *       {@code ON DELETE CASCADE} only covers a future hard purge of soft-deleted rows.</li>
     * </ul>
     * The column is unique, so a booking can never belong to two plans.
     */
    @Column(name = "timereport_id", nullable = false, unique = true)
    private Long timereportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_budget_id", nullable = false)
    private OrderBudget orderBudget;

}
