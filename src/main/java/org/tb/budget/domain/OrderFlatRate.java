package org.tb.budget.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.tb.common.domain.AuditedEntity;
import org.tb.common.util.DateUtils;

/**
 * A flat rate agreed for an order: an amount that falls due on a date rather than being earned by
 * the hour (#972). A maintenance contract, an initial fee, the instalments of a fixed price order.
 *
 * <p>Flat rates and hourly rates ({@link OrderPricing}) sit next to each other and add up. The
 * mixed case is the normal one — a monthly retainer plus work billed by the hour — so neither kind
 * excludes the other on an order.
 *
 * <p>The scope follows {@link OrderPricing}: no suborder means the rate applies to the customer
 * order as a whole, a suborder means it applies to that suborder and its subtree. Unlike a rate the
 * suborder is a concrete complete order sign, not a pattern — a flat rate is a single agreed amount
 * and has nothing to spread across several matches.
 */
@Entity
@Table(name = "order_flat_rate")
@Getter
@Setter
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class OrderFlatRate extends AuditedEntity {

    @Column(name = "customerorder_sign", nullable = false)
    private String customerorderSign;

    /**
     * The complete order sign of the suborder ({@code Suborder#getCompleteOrderSign()}, e.g.
     * {@code ORDER/01/02}). {@code null} means the flat rate applies to the whole customer order.
     */
    @Column(name = "suborder_sign")
    private String suborderSign;

    @Column
    private String description;

    @Column(length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private FlatRateRhythm rhythm;

    /**
     * The amount of every due date of {@link FlatRateRhythm#ONCE} and
     * {@link FlatRateRhythm#MONTHLY}; {@code null} for instalments, which carry an amount each.
     */
    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    /** The first due date. For {@link FlatRateRhythm#ONCE} it is the only one. */
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    /**
     * The last day a due date may fall on. Never open ended, unlike an hourly rate: an open end
     * would let a monthly flat rate run up an unbounded revenue nobody agreed to.
     */
    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @OneToMany(mappedBy = "orderFlatRate", cascade = CascadeType.ALL, orphanRemoval = true)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<OrderFlatRateInstalment> instalments = new ArrayList<>();

    /**
     * Whether the flat rate has not expired yet — the same rule as
     * {@link OrderPricing#getCurrentlyValid()}, so the list filters of the two modules mean the same
     * thing. A start in the future does not make it invalid but merely not yet due.
     */
    public boolean getCurrentlyValid() {
        return !DateUtils.today().isAfter(validUntil);
    }

    /** Whether this flat rate applies to the customer order as a whole. */
    public boolean isOrderWide() {
        return BudgetScope.isOrderWide(suborderSign);
    }

    /**
     * The first suborder level this flat rate belongs to, or {@code null} when it is order-wide —
     * the level budget plans live on, and therefore what decides which plan may hold it.
     */
    public String firstLevelSign() {
        return BudgetScope.firstLevelSignOf(suborderSign);
    }

    /**
     * The amounts falling due between the two days, both boundaries included.
     *
     * <p>The span is what the caller is looking at, not the validity of the flat rate: the
     * controlling reports the amounts of the whole span up to the end of its window (#917), so it
     * asks for that span and gets nothing from outside it.
     */
    public List<FlatRateDueAmount> dueAmountsWithin(LocalDate from, LocalDate until) {
        return switch (rhythm) {
            case ONCE -> within(validFrom, from, until)
                ? List.of(new FlatRateDueAmount(this, validFrom, amount))
                : List.of();
            case MONTHLY -> monthlyDueAmounts(from, until);
            case INSTALMENTS -> instalments.stream()
                .filter(instalment -> within(instalment.getDue(), from, until))
                .map(instalment -> new FlatRateDueAmount(this, instalment.getDue(), instalment.getAmount()))
                .sorted(Comparator.comparing(FlatRateDueAmount::due))
                .toList();
        };
    }

    /**
     * One amount per month, on the day of the month the validity starts on.
     *
     * <p>Every date is computed from {@link #validFrom} rather than from its predecessor, because
     * {@code plusMonths} clamps to the length of the target month: stepping month by month would
     * carry a shortened day of month forward for good, so a rate starting on the 31st would be due
     * on the 28th from March onwards.
     */
    private List<FlatRateDueAmount> monthlyDueAmounts(LocalDate from, LocalDate until) {
        var last = validUntil.isBefore(until) ? validUntil : until;
        var dueAmounts = new ArrayList<FlatRateDueAmount>();
        for (int month = 0; ; month++) {
            var due = validFrom.plusMonths(month);
            if (due.isAfter(last)) {
                return dueAmounts;
            }
            if (!due.isBefore(from)) {
                dueAmounts.add(new FlatRateDueAmount(this, due, amount));
            }
        }
    }

    private static boolean within(LocalDate day, LocalDate from, LocalDate until) {
        return !day.isBefore(from) && !day.isAfter(until);
    }

}
