package org.tb.budget.controller;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * The evaluated window of the segment listing (#779).
 *
 * <p>Unlike the single order view, this page evaluates every budgeted order, so it is never shown
 * without a window: an unbounded evaluation would read the whole history of every order for a page
 * nobody asked that of. The default is the running quarter, which is the period the figures are
 * usually read for.
 */
@Getter
@Setter
public class SegmentControllingFilterForm {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate until;

    /**
     * Fills in what the request left open. Both dates are defaulted on their own, so narrowing one
     * end of the window keeps the other where the quarter puts it — and an empty form falls back to
     * the running quarter as a whole.
     */
    public void applyDefaults(LocalDate today) {
        if (from == null) {
            from = today.with(IsoFields.DAY_OF_QUARTER, 1);
        }
        if (until == null) {
            until = from.with(IsoFields.DAY_OF_QUARTER, 1).plusMonths(3).minusDays(1);
        }
    }
}
