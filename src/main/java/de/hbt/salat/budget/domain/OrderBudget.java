package de.hbt.salat.budget.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;

@Entity
@Table(name = "order_budget")
@Getter
@Setter
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class OrderBudget extends AuditedEntity {

    @Column(nullable = false)
    private String name;

    /**
     * The customer order of the plan (#1205). Required since Changeset 119 (#1212): the migration
     * could resolve every stored sign.
     *
     * <p>A reference to master data of another module (#1367, ADR-0036): read only, no cascade.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customerorder_id", nullable = false)
    private Customerorder customerorder;

    /**
     * The suborder the plan lives on; it covers that suborder and everything below it
     * (→ {@link BudgetScope}). {@code null} means the budget applies to the whole customer order.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suborder_id")
    private Suborder suborder;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "alert_threshold_percent")
    private Integer alertThresholdPercent;

    @Column(name = "alert_sent_at")
    private LocalDate alertSentAt;

    @Column(name = "progress_mode", length = 10)
    @Enumerated(EnumType.STRING)
    private ProgressMode progressMode;

    @OneToMany(mappedBy = "orderBudget", cascade = CascadeType.ALL, orphanRemoval = true)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<OrderBudgetAdjustment> adjustments = new ArrayList<>();

    @OneToMany(mappedBy = "orderBudget", cascade = CascadeType.ALL, orphanRemoval = true)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<OrderBudgetScopeEntry> scopeEntries = new ArrayList<>();

    /** Whether the plan applies to the customer order as a whole. */
    public boolean isOrderWide() {
        return suborder == null;
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
