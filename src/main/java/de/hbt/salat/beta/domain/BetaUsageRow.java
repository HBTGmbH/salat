package de.hbt.salat.beta.domain;

import java.time.LocalDate;

/**
 * A row of {@link BetaUsage} as the evaluation reads it (#1447). It carries the person's id so that
 * the evaluation can count people; it never leaves the beta module's services.
 */
public record BetaUsageRow(String eventKey, long employeeId, LocalDate usageDate, BetaVariant variant,
                           int useCount) {
}
