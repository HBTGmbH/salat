package org.tb.budget.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A flat rate as it is written (#972). {@code amount} belongs to {@link FlatRateRhythm#ONCE} and
 * {@link FlatRateRhythm#MONTHLY}; instalments carry their own and are maintained one by one.
 */
public record OrderFlatRateData(
    String customerorderSign,
    String suborderSign,
    String description,
    FlatRateRhythm rhythm,
    BigDecimal amount,
    LocalDate validFrom,
    LocalDate validUntil
) {}
