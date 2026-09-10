package org.tb.budget.domain;

import lombok.Getter;

/**
 * How the due dates of a flat rate follow from its definition (#972).
 *
 * <p>The rhythm is what keeps a maintenance contract one record instead of twelve rows a year. It
 * decides which fields carry the amount: {@link #ONCE} and {@link #MONTHLY} have one amount that
 * every due date repeats, {@link #INSTALMENTS} carries an amount per date.
 */
@Getter
public enum FlatRateRhythm {

    /** One amount on one day — an initial fee, a one-off charge. */
    ONCE("main.flatrate.rhythm.once"),

    /** The same amount every month of the validity — a maintenance contract. */
    MONTHLY("main.flatrate.rhythm.monthly"),

    /** An amount per agreed date — the instalments of a fixed price order. */
    INSTALMENTS("main.flatrate.rhythm.instalments");

    private final String label;

    FlatRateRhythm(String label) {
        this.label = label;
    }

    /** Whether the definition itself carries the amount, rather than its instalments. */
    public boolean hasOwnAmount() {
        return this != INSTALMENTS;
    }

}
