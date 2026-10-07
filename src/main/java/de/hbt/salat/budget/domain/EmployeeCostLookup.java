package de.hbt.salat.budget.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory resolver for the employee cost fallback hierarchy (suborder-specific assignment →
 * order-specific assignment → general assignment, #1343), analogous to {@link OrderPricingLookup}.
 * The order type plays no part: a standby order costs what the assignments say, like any other.
 *
 * <p>Resolving a cost per time report through the repository produced two statements for every
 * single report. Both tables are small, so they are loaded once and every lookup is answered
 * from memory.
 *
 * <p>Overlapping validity ranges are rejected on save, so at most one row can match a key and
 * date. Should overlaps exist anyway, the row with the lowest id wins — that is what the
 * repository queries returned as {@code get(0)}.
 *
 * <p>The person is matched by id (#968), and so are the suborder (#1205) and the order (#1343), so a sign change — a
 * correction as much as an anonymization or a renamed order — leaves the resolution alone. An assignment whose person
 * the migration could not resolve carries no id and matches no booking, just as its sign matched none before.
 * Assignment and rate periods meet over the id of their category (#1209), so renaming a category
 * changes nothing either.
 */
public final class EmployeeCostLookup {

    /**
     * @param customerorderId {@code null} unless the assignment is for a whole order
     * @param suborderId      {@code null} unless the assignment is for a suborder
     */
    private record AssignmentKey(Long employeeId, Long customerorderId, Long suborderId) {

        static AssignmentKey general(long employeeId) {
            return new AssignmentKey(employeeId, null, null);
        }

        static AssignmentKey ofCustomerorder(long employeeId, long customerorderId) {
            return new AssignmentKey(employeeId, customerorderId, null);
        }

        static AssignmentKey ofSuborder(long employeeId, long suborderId) {
            return new AssignmentKey(employeeId, null, suborderId);
        }

    }

    private final Map<AssignmentKey, List<EmployeeCostAssignment>> assignmentsByKey;
    private final Map<Long, List<EmployeeCost>> costsByCategoryId;

    private EmployeeCostLookup(Map<AssignmentKey, List<EmployeeCostAssignment>> assignmentsByKey,
                               Map<Long, List<EmployeeCost>> costsByCategoryId) {
        this.assignmentsByKey = assignmentsByKey;
        this.costsByCategoryId = costsByCategoryId;
    }

    /** Builds a lookup over the given assignments and costs. Iteration order defines precedence. */
    public static EmployeeCostLookup of(Collection<EmployeeCostAssignment> assignments,
                                        Collection<EmployeeCost> costs) {
        Map<AssignmentKey, List<EmployeeCostAssignment>> assignmentsByKey = new HashMap<>();
        for (var assignment : assignments) {
            assignmentsByKey.computeIfAbsent(
                new AssignmentKey(assignment.getEmployeeId(), assignment.getCustomerorderId(),
                    assignment.getSuborderId()),
                k -> new ArrayList<>()).add(assignment);
        }
        Map<Long, List<EmployeeCost>> costsByCategoryId = new HashMap<>();
        for (var cost : costs) {
            costsByCategoryId.computeIfAbsent(cost.getCategory().getId(), k -> new ArrayList<>()).add(cost);
        }
        return new EmployeeCostLookup(assignmentsByKey, costsByCategoryId);
    }

    /**
     * The cost of one hour the employee books on that suborder of that order: the assignment to the
     * suborder, otherwise the one to the order, otherwise the general one. Without any of them there is
     * no rate, which means 0 EUR.
     *
     * @param customerorderId the order of the suborder; {@code null} skips the order step
     * @param suborderId      {@code null} skips the suborder step
     */
    public Optional<EmployeeCost> findEffectiveCost(long employeeId, Long customerorderId, Long suborderId,
                                                    LocalDate date) {
        var assignment = Optional.<EmployeeCostAssignment>empty();
        if (suborderId != null) {
            assignment = findAssignment(AssignmentKey.ofSuborder(employeeId, suborderId), date);
        }
        if (assignment.isEmpty() && customerorderId != null) {
            assignment = findAssignment(AssignmentKey.ofCustomerorder(employeeId, customerorderId), date);
        }
        if (assignment.isEmpty()) {
            assignment = findAssignment(AssignmentKey.general(employeeId), date);
        }
        return assignment.flatMap(a -> findCategoryCost(a.getCategory().getId(), date));
    }

    private Optional<EmployeeCostAssignment> findAssignment(AssignmentKey key, LocalDate date) {
        return assignmentsByKey.getOrDefault(key, List.of()).stream()
            .filter(a -> !a.getValidFrom().isAfter(date) && !a.getValidUntil().isBefore(date))
            .findFirst();
    }

    /**
     * The rate period of a category in force on that day — what an hour of the category costs then,
     * whoever works it. The calculation of a fixed price prices its hours by category (#1404).
     */
    public Optional<EmployeeCost> findCategoryCost(long categoryId, LocalDate date) {
        return costsByCategoryId.getOrDefault(categoryId, List.of()).stream()
            .filter(c -> !c.getValidFrom().isAfter(date) && !c.getValidUntil().isBefore(date))
            .findFirst();
    }

}
