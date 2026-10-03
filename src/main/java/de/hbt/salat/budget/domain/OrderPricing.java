package de.hbt.salat.budget.domain;

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
import de.hbt.salat.common.Validity;
import de.hbt.salat.common.domain.AuditedEntity;

@Entity
@Table(name = "order_pricing")
@Getter
@Setter
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class OrderPricing extends AuditedEntity {

    /**
     * The customer order the rate prices (#1212). {@code null} only where the migration could not
     * resolve the stored sign — see {@link #isUnresolved()}.
     */
    @Column(name = "customerorder_id")
    private Long customerorderId;

    /**
     * The sign of {@link #customerorderId}, kept because reports and ETL definitions still join on it
     * (#1212, the two-step way of #968). Written from the order on save and on every change of the
     * order ({@code OrderReferenceService#followOrderTree}); the application resolves by the id alone.
     */
    @Column(name = "customerorder_sign", nullable = false)
    private String customerorderSign;

    /**
     * A {@code LIKE} pattern over the complete order sign of the suborder ({@code
     * Suborder#getCompleteOrderSign()}, e.g. {@code ORDER/01/02}) — not the bare {@code
     * Suborder#getSign()}, and no reference: it stays as typed (#1212, → {@link OrderPricingLookup}).
     * {@code null} means the price applies to the whole customer order.
     */
    @Column(name = "suborder_sign")
    private String suborderSign;

    /**
     * The person the rate applies to (#968); {@code null} means everyone on the order — unless
     * {@link #employeeSign} is set, see {@link #isEmployeeUnresolved()}.
     */
    @Column(name = "employee_id")
    private Long employeeId;

    /**
     * The sign of {@link #employeeId}, kept only because views, ETL definitions and reports still
     * join on it (#968). The application reads it for one thing alone, telling an unresolved rate
     * from one for everyone; otherwise it is written on save from the chosen person and follows a
     * sign change ({@code EmployeeSignChangedListener}). It goes away once those readers have moved
     * to {@code employee_id}.
     */
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
        return !Validity.isInactive(validUntil);
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
        return isBlank(suborderSign) && isForEveryone() && orderBudget == null;
    }

    /**
     * Whether the migration could not resolve the order the rate was stored with (#1212): a sign
     * carried by no order or by several. Such a rate prices nothing until an order is picked.
     */
    public boolean isUnresolved() {
        return customerorderId == null;
    }

    /** Whether the rate applies to every person on the order — it names no person at all. */
    public boolean isForEveryone() {
        return employeeId == null && isBlank(employeeSign);
    }

    /**
     * Whether the rate names a person the migration could not resolve (#968): a sign carried by no
     * person or by several, and no id. Such a rate applies to nobody, exactly as it did while rates
     * were matched by sign — reading the missing id as "everyone" would hand one person's rate to
     * the whole order.
     */
    public boolean isEmployeeUnresolved() {
        return employeeId == null && !isBlank(employeeSign);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
