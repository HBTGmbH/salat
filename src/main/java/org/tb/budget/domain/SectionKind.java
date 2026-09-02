package org.tb.budget.domain;

/**
 * What a controlling section reports on. An order is budgeted either as a whole or per first level
 * suborder at any point in time, never both, so a section is one of these three and never a mix.
 */
public enum SectionKind {
    /** One plan covering the whole customer order. */
    ORDER_LEVEL,
    /** One plan per first level suborder, all sharing the same period. */
    SUBORDER_LEVEL,
    /** Time no plan covers. Every row carries its own periods. */
    UNPLANNED
}
