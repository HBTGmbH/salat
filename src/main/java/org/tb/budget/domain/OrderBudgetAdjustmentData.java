package org.tb.budget.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OrderBudgetAdjustmentData(
    BigDecimal amount,
    LocalDate effective,
    String comment
) {}
