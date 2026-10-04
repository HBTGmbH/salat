package de.hbt.salat.budget.domain;

import java.util.List;

/**
 * One row of the cost category overview (#954): the category name and the employees it is assigned
 * to right now or from some point in the future.
 *
 * <p>The category itself is a {@link CostCategory} (#1209); several {@link EmployeeCost} rows share it to model
 * a rate that changed over time. Expired assignments are left out: the overview answers who is
 * costed this way, and someone whose assignment ended last year is not.
 */
public record EmployeeCostCategory(String name, List<String> employeeSigns) {

    public boolean hasEmployees() {
        return !employeeSigns.isEmpty();
    }

}
