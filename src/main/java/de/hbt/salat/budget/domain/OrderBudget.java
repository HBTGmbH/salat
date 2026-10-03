package de.hbt.salat.budget.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "order_budget")
@Getter
@Setter
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class OrderBudget extends AuditedEntity {

    @Column(nullable = false)
    private String name;

    /**
     * The customer order of the plan (#1205). {@code null} only on a row the migration could not
     * resolve: its sign was carried by no order or by several. Such a plan covers nothing and is
     * marked in the list until somebody picks the order.
     */
    @Column(name = "customerorder_id")
    private Long customerorderId;

    /**
     * The sign of {@link #customerorderId}, kept because reports still read it (#1205, the way of
     * #968). The application does not resolve anything through it: it is written on save from the
     * chosen order and follows a rename ({@code OrderSignMirrorListener}).
     */
    @Column(name = "customerorder_sign", nullable = false)
    private String customerorderSign;

    /**
     * The suborder the plan lives on; it covers that suborder and everything below it
     * (→ {@link BudgetScope}). {@code null} with a {@link #suborderSign} means the migration could
     * not resolve the suborder — the plan then covers nothing rather than the whole order.
     */
    @Column(name = "suborder_id")
    private Long suborderId;

    /**
     * The complete order sign of {@link #suborderId} ({@code Suborder#getCompleteOrderSign()},
     * e.g. {@code ORDER/01/02}), kept as a mirror like {@link #customerorderSign}. {@code null}
     * means the budget applies to the whole customer order.
     */
    @Column(name = "suborder_sign")
    private String suborderSign;

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

    /** Whether the plan applies to the customer order as a whole (→ {@link BudgetScope#isOrderWide}). */
    public boolean isOrderWide() {
        return BudgetScope.isOrderWide(suborderId, suborderSign);
    }

    /**
     * Whether the migration could not resolve what the plan refers to (#1205): the order, or a
     * suborder it names. Such a plan covers nothing.
     */
    public boolean isUnresolved() {
        return customerorderId == null || (!isOrderWide() && suborderId == null);
    }

}
