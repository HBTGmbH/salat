package org.tb.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.tb.common.domain.AuditedEntity;

@Getter
@Setter
@Entity
@Table(name = "order_revenue")
public class OrderRevenue extends AuditedEntity {

    @ManyToOne
    @JoinColumn(name = "customerorder_id", nullable = false)
    private Customerorder customerorder;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private BigDecimal amount;

    private String comment;

}
