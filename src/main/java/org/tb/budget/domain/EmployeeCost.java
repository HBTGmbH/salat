package org.tb.budget.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.tb.common.domain.AuditedEntity;

@Entity
@Table(name = "employee_cost")
@Getter
@Setter
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class EmployeeCost extends AuditedEntity {

    /**
     * The cost category. Deliberately not unique: several records share one name to model a rate
     * that changed over time, so the name is the category and not the key of a single record
     * (→ {@code EmployeeCostRepository#findByNameOrderByValidFromAsc}).
     */
    @Column(nullable = false)
    private String name;

    @Column(name = "cost_cents_per_hour", nullable = false)
    private Integer costCentsPerHour;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

}
