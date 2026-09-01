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
@Table(name = "order_pricing")
@Getter
@Setter
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class OrderPricing extends AuditedEntity {

    @Column(name = "customerorder_sign", nullable = false)
    private String customerorderSign;

    /**
     * The complete order sign of the suborder ({@code Suborder#getCompleteOrderSign()},
     * e.g. {@code ORDER/01/02}) — not the bare {@code Suborder#getSign()}. {@code null} means the
     * price applies to the whole customer order.
     */
    @Column(name = "suborder_sign")
    private String suborderSign;

    @Column(name = "employee_sign")
    private String employeeSign;

    @Column
    private String description;

    @Column(name = "price_cents_per_hour", nullable = false)
    private Integer priceCentsPerHour;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

}
