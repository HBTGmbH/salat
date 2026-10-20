package de.hbt.salat.common.achievement;

import java.time.LocalDate;

/**
 * A level of an achievement a person has reached (#1501), with the day it was reached — the day of
 * the matter, not the day the job noticed it: the anniversary of a person who joined in 2017 is
 * recorded with its date in 2022 even when the job first runs years later.
 */
public record ReachedAchievement(long employeeId, String key, int level, LocalDate achievedOn) {
}
