package de.hbt.salat.common.achievement;

import static de.hbt.salat.common.achievement.AchievementLevel.level;
import static de.hbt.salat.common.achievement.AchievementTier.BRONZE;
import static de.hbt.salat.common.achievement.AchievementTier.GOLD;
import static de.hbt.salat.common.achievement.AchievementTier.SILVER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class AchievementDefinitionTest {

  private static final AchievementDefinition TENURE = new AchievementDefinition("tenure", "calendar", 20,
      List.of(level(1, BRONZE), level(5, SILVER), level(10, GOLD)));

  @Test
  void the_next_level_follows_the_highest_reached_one() {
    assertThat(TENURE.nextLevelAfter(null)).contains(1);
    assertThat(TENURE.nextLevelAfter(1)).contains(5);
    assertThat(TENURE.nextLevelAfter(10)).isEmpty();
  }

  @Test
  void every_level_has_its_tier() {
    assertThat(TENURE.getLevels()).containsExactly(1, 5, 10);
    assertThat(TENURE.tierOf(5)).isEqualTo(SILVER);
    assertThatThrownBy(() -> TENURE.tierOf(2)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void the_message_keys_follow_the_key() {
    assertThat(TENURE.groupKey()).isEqualTo("main.achievement.tenure.group");
    assertThat(TENURE.titleKey()).isEqualTo("main.achievement.tenure.title");
    assertThat(TENURE.rankKey(5)).isEqualTo("main.achievement.tenure.rank.5");
    assertThat(TENURE.remainingKey()).isEqualTo("main.achievement.tenure.remaining");
  }

  @Test
  void a_key_or_levels_that_do_not_fit_are_rejected() {
    assertThatThrownBy(() -> new AchievementDefinition("Tenure", "calendar", 1, List.of(level(1, BRONZE))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AchievementDefinition("tenure", "calendar", 1, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AchievementDefinition("tenure", "calendar", 1, List.of(level(5, BRONZE), level(1, SILVER))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AchievementDefinition("tenure", "calendar", 1, List.of(level(1, GOLD), level(5, BRONZE))))
        .as("a higher level never gets a lower tier").isInstanceOf(IllegalArgumentException.class);
  }
}
