package de.hbt.salat.beta.domain;

import java.time.LocalDate;

/** A beta a person answered the question about (#1501): who, which, when - not what. */
public record AnsweredBeta(long employeeId, String featureKey, LocalDate answeredOn) {
}
