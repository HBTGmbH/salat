package de.hbt.salat.budget.domain;

import java.time.LocalDate;

public record OrderBudgetData(
    String name,
    String customerorderSign,
    String suborderSign,
    LocalDate validFrom,
    LocalDate validUntil,
    Boolean active,
    Integer alertThresholdPercent,
    ProgressMode progressMode
) {}
