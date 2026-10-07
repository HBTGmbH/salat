package de.hbt.salat.budget.domain;

import java.time.LocalDate;

/**
 * A plan as it is written. Order and suborder by id (#1205); the signs are written from the records.
 *
 * @param suborderId {@code null} for a plan on the whole customer order
 * @param fixedPrice whether the plan is a fixed price (#1404); such a plan measures its progress by
 *                   hand, whatever {@code progressMode} says
 */
public record OrderBudgetData(
    String name,
    Long customerorderId,
    Long suborderId,
    LocalDate validFrom,
    LocalDate validUntil,
    Boolean active,
    Integer alertThresholdPercent,
    ProgressMode progressMode,
    boolean fixedPrice
) {}
