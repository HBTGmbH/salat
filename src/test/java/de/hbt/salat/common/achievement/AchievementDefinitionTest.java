package de.hbt.salat.common.achievement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class AchievementDefinitionTest {

  private static final AchievementDefinition TENURE = new AchievementDefinition("tenure", List.of(1, 5, 10), "trophy");

  @Test
  void the_next_level_follows_the_highest_reached_one() {
    assertThat(TENURE.nextLevelAfter(null)).contains(1);
    assertThat(TENURE.nextLevelAfter(1)).contains(5);
    assertThat(TENURE.nextLevelAfter(10)).isEmpty();
  }

  @Test
  void the_message_keys_follow_the_key() {
    assertThat(TENURE.groupKey()).isEqualTo("main.achievement.tenure.group");
    assertThat(TENURE.titleKey()).isEqualTo("main.achievement.tenure.title");
    assertThat(TENURE.levelKey()).isEqualTo("main.achievement.tenure.level");
  }

  @Test
  void a_key_or_levels_that_do_not_fit_are_rejected() {
    assertThatThrownBy(() -> new AchievementDefinition("Tenure", List.of(1), "trophy"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AchievementDefinition("tenure", List.of(), "trophy"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AchievementDefinition("tenure", List.of(5, 1), "trophy"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AchievementDefinition("tenure", List.of(0, 1), "trophy"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
