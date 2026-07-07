package org.tb.budget.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BudgetDashboardRow(
    long budgetId,
    String budgetName,
    String customerorderSign,
    String customerorderName,
    LocalDate validFrom,
    LocalDate validUntil,
    BigDecimal budgetEuro,
    BigDecimal coveredRevenueEuro,
    Integer alertThresholdPercent,
    double utilizationPercent
) {
    public boolean hasBudget() { return budgetEuro != null && budgetEuro.signum() != 0; }
    public boolean hasAlertThreshold() { return alertThresholdPercent != null; }
    public boolean isAboveThreshold() { return hasAlertThreshold() && utilizationPercent >= alertThresholdPercent; }
}
