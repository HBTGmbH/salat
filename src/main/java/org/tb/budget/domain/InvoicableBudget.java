package org.tb.budget.domain;

import java.time.LocalDate;

/**
 * A budget plan as everything outside the budget module may see it (#915): flat data, no entity.
 *
 * <p>The invoicing picks a plan to bill and needs its name and validity, nothing more. Handing out
 * {@code OrderBudget} instead would carry its adjustments, scope entries and lazy associations
 * across the module boundary and make every consumer a client of the budget persistence model.
 */
public record InvoicableBudget(
    long id,
    String name,
    String customerorderSign,
    LocalDate validFrom,
    LocalDate validUntil) {

    public static InvoicableBudget from(OrderBudget budget) {
        return new InvoicableBudget(budget.getId(), budget.getName(), budget.getCustomerorderSign(),
            budget.getValidFrom(), budget.getValidUntil());
    }

}
