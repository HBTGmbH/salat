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

@Entity
@Table(name = "order_budget_scope_entry")
@Getter
@Setter
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class OrderBudgetScopeEntry extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_budget_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_order_budget_scope_entry_budget"))
    private OrderBudget orderBudget;

    @Column(nullable = false)
    private LocalDate refdate;

    @Column(nullable = false)
    private Integer percent;

    @Column(length = 2000)
    private String comment;
}
