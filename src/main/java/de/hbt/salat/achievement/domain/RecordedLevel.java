package de.hbt.salat.achievement.domain;

/** What identifies a recorded {@link Achievement} (#1501): the job records each one once. */
public record RecordedLevel(long employeeId, String key, int level) {
}
