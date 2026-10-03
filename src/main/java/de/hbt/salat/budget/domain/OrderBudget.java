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
     * The customer order of the plan (#1205). Required since Changeset 119 (#1212): the migration
     * could resolve every stored sign.
     */
    @Column(name = "customerorder_id", nullable = false)
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
     * (→ {@link BudgetScope}). {@code null} means the budget applies to the whole customer order.
     */
    @Column(name = "suborder_id")
    private Long suborderId;

    /**
     * The complete order sign of {@link #suborderId} ({@code Suborder#getCompleteOrderSign()},
     * e.g. {@code ORDER/01/02}), kept as a mirror like {@link #customerorderSign}; the application
     * never reads it (#1212).
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

    /** Whether the plan applies to the customer order as a whole. */
    public boolean isOrderWide() {
        return suborderId == null;
    }

}
