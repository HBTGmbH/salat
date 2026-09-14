package org.tb.budget.domain;

import java.math.BigDecimal;

/**
 * The two rates that apply to one booking — or to a group of bookings resolved alike (#964).
 *
 * <p>The difference between "no rate" and "0 EUR" is carried as {@code null} versus {@code 0}, and
 * it is decided by what the resolution found, never by the amount that comes out of it: a condition
 * of 0,00 EUR/h is a deliberate statement — unpaid work, goodwill, settled elsewhere — while a
 * missing one means the work enters the margin with 0 EUR revenue without anybody having said so.
 * {@code BudgetControllingService.costOf} and {@code rateOf} end on {@code orElse(ZERO)} and cannot
 * tell the two apart any more, which is exactly what this record exists to keep.
 *
 * <p>{@code invoiceable} takes precedence over both. Work on a suborder that is not invoiceable is
 * never billed, whatever rate matches ({@code scoreReports}), so 0 EUR revenue is right there and
 * must not be reported as a gap.
 *
 * <p>{@code costsIncluded} says whether the cost side was resolved at all: it is reported to
 * managers only, and for everybody else the lookup is not even loaded. Without the flag a page that
 * does not report costs would look like a page on which no cost rate applies.
 */
public record AppliedRate(String costName, Integer costCentsPerHour, Integer priceCentsPerHour,
                          boolean invoiceable, boolean costsIncluded) {

    /** What applies where neither side is resolved — the bookings of a suborder nobody knows. */
    public static AppliedRate none(boolean costsIncluded) {
        return new AppliedRate(null, null, null, true, costsIncluded);
    }

    public boolean hasCost() {
        return costCentsPerHour != null;
    }

    public boolean hasPrice() {
        return priceCentsPerHour != null;
    }

    /** Reported only where costs are reported at all — see the class comment. */
    public boolean missingCost() {
        return costsIncluded && costCentsPerHour == null;
    }

    /** Not reported on a suborder that is not invoiceable: 0 EUR revenue is right there. */
    public boolean missingPrice() {
        return invoiceable && priceCentsPerHour == null;
    }

    public boolean notInvoiceable() {
        return !invoiceable;
    }

    public BigDecimal costEuroPerHour() {
        return euro(costCentsPerHour);
    }

    public BigDecimal priceEuroPerHour() {
        return euro(priceCentsPerHour);
    }

    private static BigDecimal euro(Integer cents) {
        return cents == null ? null : new BigDecimal(cents).movePointLeft(2);
    }

}
