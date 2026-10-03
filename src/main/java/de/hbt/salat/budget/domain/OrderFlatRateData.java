package de.hbt.salat.budget.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A flat rate as it is written (#972). {@code amount} belongs to {@link FlatRateRhythm#ONCE} and
 * {@link FlatRateRhythm#MONTHLY}; instalments carry their own and are maintained one by one.
 *
 * @param customerorderId the customer order, by id (#1205)
 * @param suborderId      the suborder, {@code null} for the whole customer order
 * @param orderBudgetId the budget plan the amounts count against, or {@code null} to leave the
 *                      allocation to be derived as before (#1065, → {@link FlatRateAllocation})
 */
public record OrderFlatRateData(
    Long customerorderId,
    Long suborderId,
    Long orderBudgetId,
    String description,
    FlatRateRhythm rhythm,
    BigDecimal amount,
    LocalDate validFrom,
    LocalDate validUntil
) {}
