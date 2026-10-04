package de.hbt.salat.budget.viewhelper;

import de.hbt.salat.budget.domain.EmployeeCostAssignment;

/**
 * One assignment of the category page, together with the sign it is shown with (#968).
 *
 * <p>The sign is the person's current one, read off the person rather than the assignment, so it
 * follows a rename. Only an assignment whose person the migration could not resolve shows the sign
 * it was stored with — that is all there is to recognize it by, and the page marks it.
 */
/**
 * @param customerorderSign the sign the customer order has today, read by id (#1343); {@code null} unless
 *                          the assignment is for a whole order
 * @param suborderSign      the complete sign the suborder has today, read by id (#1212); {@code null}
 *                          unless the assignment is for a suborder
 */
public record EmployeeCostAssignmentViewHelper(EmployeeCostAssignment assignment, String employeeSign,
                                               String customerorderSign, String suborderSign) {

    public boolean employeeUnknown() {
        return assignment.isEmployeeUnresolved();
    }

    /**
     * What the assignment is for (#1343): the sign of the order, or the complete sign of the suborder —
     * {@code ORDER} against {@code ORDER/01} tells the two apart. {@code null} for a general one.
     */
    public String scopeSign() {
        return assignment.isCustomerorderSpecific() ? customerorderSign : suborderSign;
    }

}
