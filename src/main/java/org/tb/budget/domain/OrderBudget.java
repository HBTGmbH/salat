package org.tb.budget.domain;

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
import org.tb.common.domain.AuditedEntity;

@Entity
@Table(name = "order_budget")
@Getter
@Setter
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class OrderBudget extends AuditedEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "customerorder_sign", nullable = false)
    private String customerorderSign;

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

}
