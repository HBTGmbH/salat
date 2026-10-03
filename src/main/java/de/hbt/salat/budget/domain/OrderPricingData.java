package de.hbt.salat.budget.domain;

import java.time.LocalDate;

/**
 * A customer rate as it is written.
 *
 * @param customerorderId the order the rate prices (#1212)
 * @param employeeId    the person the rate applies to, or {@code null} for everyone on the order
 * @param orderBudgetId the budget plan the rate is bound to, or {@code null} for a rate that
 *                      applies whatever plan a booking belongs to (#1065)
 */
public record OrderPricingData(
    Long customerorderId,
    String suborderSign,
    Long employeeId,
    Long orderBudgetId,
    String description,
    Integer priceCentsPerHour,
    LocalDate validFrom,
    LocalDate validUntil
) {}
