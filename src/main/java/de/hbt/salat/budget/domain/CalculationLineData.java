package de.hbt.salat.budget.domain;

import java.math.BigDecimal;

/**
 * A line of a fixed-price calculation as it is written (#1404).
 *
 * @param id    the line to change, {@code null} for a new one
 * @param hours the calculated hours, decimal — 7.5 is seven and a half hours
 */
public record CalculationLineData(Long id, Long suborderId, Long categoryId, BigDecimal hours) {}
