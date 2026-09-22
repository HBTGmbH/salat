package org.tb.budget.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.tb.common.domain.AuditedEntity;
import org.tb.common.util.DateUtils;

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

    /**
     * The budget plan this rate is bound to; {@code null} means it applies whatever plan a booking
     * belongs to (#1065). A bound rate is a specificity level of its own — more specific than the
     * suborder pattern, less specific than the employee — and it applies only to bookings assigned
     * to that plan; every other booking falls back to the plan-less rate (→ {@link
     * OrderPricingLookup}).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_budget_id")
    private OrderBudget orderBudget;

    @Column
    private String description;

    @Column(name = "price_cents_per_hour", nullable = false)
    private Integer priceCentsPerHour;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    /**
     * Whether the pricing has not expired yet — the same rule as
     * {@code Employeecontract#getCurrentlyValid()} and {@code Suborder#getCurrentlyValid()}: an end
     * on today still counts, and an open end never expires. Unlike those two, an open end is stored
     * as the sentinel 31.12.2999 rather than as {@code null}, so it needs no case of its own.
     *
     * <p>A start in the future does not make a pricing invalid but merely not yet in effect; it
     * stays in the list, or a rate entered ahead of time would be entered a second time.
     */
    public boolean getCurrentlyValid() {
        return !DateUtils.today().isAfter(validUntil);
    }

    /**
     * The id of the bound plan without loading it. Reading the identifier off a lazy proxy does not
     * initialize it, so the rate resolution stays free of a query per rate — which is the whole
     * reason {@link OrderPricingLookup} exists.
     */
    public Long getOrderBudgetId() {
        return orderBudget == null ? null : orderBudget.getId();
    }

    /**
     * Whether this rate prices the customer order as a whole — no suborder pattern, no employee, no
     * budget plan. It is the only kind that claims to cover the order period, which is what the
     * coverage check of the rate list judges (#957, → {@code OrderPricingLookup#hasUncoveredPeriod}).
     *
     * <p>A plan-bound rate is deliberately not order-wide even without a pattern (#1065): it prices
     * only the bookings of its plan, so it leaves every other day of the order unpriced and must not
     * silence the gap warning.
     */
    public boolean isOrderWide() {
        return isBlank(suborderSign) && isBlank(employeeSign) && orderBudget == null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
