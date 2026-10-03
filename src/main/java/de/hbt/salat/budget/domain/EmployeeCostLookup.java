package de.hbt.salat.budget.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import de.hbt.salat.order.domain.OrderType;

/**
 * In-memory resolver for the employee cost fallback hierarchy
 * (suborder-specific assignment → general assignment, the latter only where the suborder is no
 * standby order), analogous to {@link OrderPricingLookup}.
 *
 * <p>Resolving a cost per time report through the repository produced two statements for every
 * single report. Both tables are small, so they are loaded once and every lookup is answered
 * from memory.
 *
 * <p>Overlapping validity ranges are rejected on save, so at most one row can match a key and
 * date. Should overlaps exist anyway, the row with the lowest id wins — that is what the
 * repository queries returned as {@code get(0)}.
 *
 * <p>The person is matched by id (#968), and so is the suborder (#1205), so a sign change — a
 * correction as much as an anonymization or a renamed order — leaves the resolution alone. An assignment the migration could not resolve
 * carries no id and matches no booking, just as its sign matched none before.
 */
public final class EmployeeCostLookup {

    /**
     * @param suborderId {@code null} for the general assignment of the person
     * @param specific   whether the assignment names a suborder — a specific one the migration could
     *                   not resolve keeps {@code true} with a {@code null} id and so never turns into
     *                   the general one (#1205)
     */
    private record AssignmentKey(Long employeeId, Long suborderId, boolean specific) {

        static AssignmentKey general(long employeeId) {
            return new AssignmentKey(employeeId, null, false);
        }

        static AssignmentKey specific(long employeeId, long suborderId) {
            return new AssignmentKey(employeeId, suborderId, true);
        }

    }

    private final Map<AssignmentKey, List<EmployeeCostAssignment>> assignmentsByKey;
    private final Map<String, List<EmployeeCost>> costsByName;

    private EmployeeCostLookup(Map<AssignmentKey, List<EmployeeCostAssignment>> assignmentsByKey,
                               Map<String, List<EmployeeCost>> costsByName) {
        this.assignmentsByKey = assignmentsByKey;
        this.costsByName = costsByName;
    }

    /** Builds a lookup over the given assignments and costs. Iteration order defines precedence. */
    public static EmployeeCostLookup of(Collection<EmployeeCostAssignment> assignments,
                                        Collection<EmployeeCost> costs) {
        Map<AssignmentKey, List<EmployeeCostAssignment>> assignmentsByKey = new HashMap<>();
        for (var assignment : assignments) {
            assignmentsByKey.computeIfAbsent(
                new AssignmentKey(assignment.getEmployeeId(), assignment.getSuborderId(),
                    assignment.isSuborderSpecific()),
                k -> new ArrayList<>()).add(assignment);
        }
        Map<String, List<EmployeeCost>> costsByName = new HashMap<>();
        for (var cost : costs) {
            costsByName.computeIfAbsent(cost.getName(), k -> new ArrayList<>()).add(cost);
        }
        return new EmployeeCostLookup(assignmentsByKey, costsByName);
    }

    /**
     * The cost of one hour the employee books on that suborder.
     *
     * <p>Standby costs something else than the work the general rate of an employee was made for,
     * so a standby suborder is resolved from its own assignment alone (#463). Without one there is
     * no rate — and no rate means 0 EUR, deliberately, rather than the general rate of the
     * employee, which would be wrong by a wide margin.
     */
    public Optional<EmployeeCost> findEffectiveCost(long employeeId, Long suborderId,
                                                    OrderType orderType, LocalDate date) {
        if (suborderId != null) {
            var assignment = findAssignment(AssignmentKey.specific(employeeId, suborderId), date);
            if (assignment.isPresent()) {
                return findCost(assignment.get().getEmployeeCostName(), date);
            }
        }
        if (orderType == OrderType.BEREITSCHAFT) {
            return Optional.empty();
        }
        return findAssignment(AssignmentKey.general(employeeId), date)
            .flatMap(a -> findCost(a.getEmployeeCostName(), date));
    }

    private Optional<EmployeeCostAssignment> findAssignment(AssignmentKey key, LocalDate date) {
        return assignmentsByKey.getOrDefault(key, List.of()).stream()
            .filter(a -> !a.getValidFrom().isAfter(date) && !a.getValidUntil().isBefore(date))
            .findFirst();
    }

    private Optional<EmployeeCost> findCost(String name, LocalDate date) {
        return costsByName.getOrDefault(name, List.of()).stream()
            .filter(c -> !c.getValidFrom().isAfter(date) && !c.getValidUntil().isBefore(date))
            .findFirst();
    }

}
