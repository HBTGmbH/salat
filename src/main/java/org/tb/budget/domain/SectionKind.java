package org.tb.budget.domain;

/**
 * What a controlling section reports on. At any point in time an order is budgeted either as a whole
 * or on one suborder level (#1004), never both, so a section is one of these three and never a mix.
 * Which level a suborder section sits on is a number next to the kind
 * ({@link BudgetControllingSection#level()}) — a depth is not a type, and an enum constant per
 * possible depth would be one.
 */
public enum SectionKind {
    /** One plan covering the whole customer order. */
    ORDER_LEVEL,
    /** One plan per suborder of the level the section sits on, all sharing the same period. */
    SUBORDER_LEVEL,
    /**
     * Bookings that belong to no plan (#913): no assignment at all, or one pointing at a plan the
     * evaluation excludes. Since only the stored assignment counts, this is where hours would
     * otherwise stop appearing in any number — it is not a period, it is a residue.
     */
    UNPLANNED
}
