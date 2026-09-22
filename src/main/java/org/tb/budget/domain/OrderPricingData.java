package org.tb.budget.domain;

import java.time.LocalDate;

/**
 * A customer rate as it is written.
 *
 * @param orderBudgetId the budget plan the rate is bound to, or {@code null} for a rate that
 *                      applies whatever plan a booking belongs to (#1065)
 */
public record OrderPricingData(
    String customerorderSign,
    String suborderSign,
    String employeeSign,
    Long orderBudgetId,
    String description,
    Integer priceCentsPerHour,
    LocalDate validFrom,
    LocalDate validUntil
) {}
