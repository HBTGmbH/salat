package de.hbt.salat.achievement.domain;

import de.hbt.salat.common.achievement.AchievementDefinition;
import java.time.LocalDate;

/** A recorded achievement the person has not yet seen announced, for the popup (#1501). */
public record UnseenAchievement(AchievementDefinition definition, int level, LocalDate achievedOn) {
}
