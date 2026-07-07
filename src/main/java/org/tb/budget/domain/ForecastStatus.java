package org.tb.budget.domain;

public enum ForecastStatus {
    GREEN,   // forecast ≤ 80% of budget
    YELLOW,  // 80% < forecast ≤ 100% of budget
    RED,     // forecast > budget
    UNKNOWN  // no budget or no forecast data
}
