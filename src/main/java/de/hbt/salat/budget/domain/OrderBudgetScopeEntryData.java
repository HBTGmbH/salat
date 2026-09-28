package de.hbt.salat.budget.domain;

import java.time.LocalDate;

public record OrderBudgetScopeEntryData(
    LocalDate refdate,
    Integer percent,
    String comment
) {}
