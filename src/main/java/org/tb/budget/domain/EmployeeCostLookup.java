package org.tb.budget.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory resolver for the employee cost fallback hierarchy
 * (suborder-specific assignment → general assignment), analogous to {@link OrderPricingLookup}.
 *
 * <p>Resolving a cost per time report through the repository produced two statements for every
 * single report. Both tables are small, so they are loaded once and every lookup is answered
 * from memory.
 *
 * <p>Overlapping validity ranges are rejected on save, so at most one row can match a key and
 * date. Should overlaps exist anyway, the row with the lowest id wins — that is what the
 * repository queries returned as {@code get(0)}.
 */
public final class EmployeeCostLookup {

    private record AssignmentKey(String employeeSign, String suborderSign) {}

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
                new AssignmentKey(assignment.getEmployeeSign(), assignment.getSuborderSign()),
                k -> new ArrayList<>()).add(assignment);
        }
        Map<String, List<EmployeeCost>> costsByName = new HashMap<>();
        for (var cost : costs) {
            costsByName.computeIfAbsent(cost.getName(), k -> new ArrayList<>()).add(cost);
        }
        return new EmployeeCostLookup(assignmentsByKey, costsByName);
    }

    public Optional<EmployeeCost> findEffectiveCost(String employeeSign, String suborderSign, LocalDate date) {
        if (suborderSign != null) {
            var assignment = findAssignment(new AssignmentKey(employeeSign, suborderSign), date);
            if (assignment.isPresent()) {
                return findCost(assignment.get().getEmployeeCostName(), date);
            }
        }
        return findAssignment(new AssignmentKey(employeeSign, null), date)
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
