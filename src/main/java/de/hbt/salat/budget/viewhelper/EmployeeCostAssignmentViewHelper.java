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
 * @param suborderSign the complete sign the suborder has today, read by id (#1212); the stored one
 *                     for an assignment the migration could not resolve, {@code null} for a general one
 */
public record EmployeeCostAssignmentViewHelper(EmployeeCostAssignment assignment, String employeeSign,
                                               String suborderSign) {

    public boolean employeeUnknown() {
        return assignment.isEmployeeUnresolved();
    }

}
