package org.tb.budget.domain;

/**
 * What a bulk assignment would do before it does it (#911). Every booking in the selected period and
 * scope falls into exactly one of the four buckets, so the numbers add up to the selection.
 *
 * @param unassigned        belongs to no plan yet — assigned in either mode
 * @param assignedElsewhere belongs to another plan — only retargeted when that option is on
 * @param alreadyOnTarget   already on the target plan; nothing to do, but shown so that a preview of
 *                          all zeros is distinguishable from "the selection is empty"
 * @param notAssignable     outside the scope or the validity of the target plan — never assigned,
 *                          the same rule as the single assignment (#908) applies
 */
public record BulkAssignmentPreview(
    BudgetBookingCounts unassigned,
    BudgetBookingCounts assignedElsewhere,
    BudgetBookingCounts alreadyOnTarget,
    BudgetBookingCounts notAssignable) {

    public static final BulkAssignmentPreview EMPTY = new BulkAssignmentPreview(
        BudgetBookingCounts.NONE, BudgetBookingCounts.NONE,
        BudgetBookingCounts.NONE, BudgetBookingCounts.NONE);

    /** Everything the selection matched, whatever happens to it. */
    public BudgetBookingCounts selected() {
        return unassigned.plus(assignedElsewhere).plus(alreadyOnTarget).plus(notAssignable);
    }

    /** What the run would actually write, given the retarget option. */
    public BudgetBookingCounts affected(boolean includeAssigned) {
        return includeAssigned ? unassigned.plus(assignedElsewhere) : unassigned;
    }

    public boolean isEmpty() {
        return selected().isEmpty();
    }

}
