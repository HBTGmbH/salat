package org.tb.budget.domain;

/**
 * One person who can be picked in a bulk assignment (#953). Only people who actually booked in the
 * selected order, suborder and period are offered, so no combination can be chosen that is
 * necessarily empty.
 */
public record BulkAssignmentEmployee(long id, String sign, String name) {

    /** Kürzel plus Name — the same shape the rest of the application uses for a person. */
    public String label() {
        return name == null || name.isBlank() ? sign : sign + " - " + name;
    }

}
