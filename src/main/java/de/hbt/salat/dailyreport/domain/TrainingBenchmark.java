package de.hbt.salat.dailyreport.domain;

import java.math.BigDecimal;

/**
 * Where the colleagues stand with their regular training (#827): the median in working days over the
 * same period as the own account, and whether it lies so far above the own training that the account
 * highlights it.
 */
public record TrainingBenchmark(BigDecimal medianDays, boolean highlighted) {
}
