package de.hbt.salat.common.achievement;

/**
 * The person an achievement is asked about (#1501), by both ids: a module that knows people only by
 * their login - the command palette - asks with the login, the others with the employee.
 */
public record AchievementPerson(long employeeId, long salatUserId) {
}
