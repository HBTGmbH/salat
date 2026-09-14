package org.tb.budget.domain;

import java.math.BigDecimal;

/**
 * A cost category with the rate it carried (#964).
 *
 * <p>The rate belongs in the key, not only the name: several {@link EmployeeCost} records share one
 * name to model a rate that changed over time, so "Senior" at 95,00 EUR and "Senior" at 100,00 EUR
 * are two answers the card has to be able to name side by side.
 */
public record CostCategoryRate(String name, int centsPerHour) {

    public BigDecimal euroPerHour() {
        return new BigDecimal(centsPerHour).movePointLeft(2);
    }

}
