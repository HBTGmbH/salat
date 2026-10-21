package de.hbt.salat.achievement.domain;

/**
 * What identifies a recorded {@link Achievement} (#1501): the job records each one once - a level of a
 * yearly achievement once per year (#1512).
 */
public record RecordedLevel(long employeeId, String key, int year, int level) {
}
