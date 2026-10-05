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
     * The customer order the rate prices (#1212); required since Changeset 119.
     */
    @Column(name = "customerorder_id", nullable = false)
    private Long customerorderId;

    /**
     * A {@code LIKE} pattern over the complete order sign of the suborder ({@code
     * Suborder#getCompleteOrderSign()}, e.g. {@code ORDER/01/02}) — not the bare {@code
     * Suborder#getSign()}, and no reference: it stays as typed (#1212, → {@link OrderPricingLookup}).
     * {@code null} means the price applies to the whole customer order.
     *
     * <p>The one place where a sign of the order tree still decides what a record covers (AGENTS.md,
     * #1205). That is intended — a pattern prices a group of suborders, including ones created
     * later — and it is why a rename has to reach it: the pattern starts with the order sign, so
     * renaming the order, or renaming or moving a suborder, changes the complete signs it is matched
     * against. Signs stay changeable (#1206, ADR-0034); {@code OrderReferenceService#followRename}
     * rewrites a pattern that names the renamed order or suborder as a whole. A pattern it cannot
     * rewrite unambiguously — a wildcard before the end of the old sign, or a suborder moved to
     * another customer order — is left as it is and named to the person saving; it then has to be
     * adjusted by hand.
     */
    @Column(name = "suborder_sign")
    private String suborderSign;

    /** The person the rate applies to (#968); {@code null} means everyone on the order. */
    @Column(name = "employee_id")
    private Long employeeId;

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

    /** Whether the rate applies to every person on the order — it names no person at all. */
    public boolean isForEveryone() {
        return employeeId == null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
