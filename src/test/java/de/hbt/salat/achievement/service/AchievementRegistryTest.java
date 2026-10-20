package de.hbt.salat.achievement.service;

import static de.hbt.salat.common.achievement.AchievementLevel.level;
import static de.hbt.salat.common.achievement.AchievementTier.BRONZE;
import static de.hbt.salat.common.achievement.AchievementTier.SILVER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hbt.salat.common.achievement.AchievementContributor;
import de.hbt.salat.common.achievement.AchievementDefinition;
import de.hbt.salat.common.achievement.ReachedAchievement;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class AchievementRegistryTest {

  private static final AchievementDefinition TENURE =
      new AchievementDefinition("tenure", "calendar", 20, List.of(level(1, BRONZE), level(5, SILVER)));
  private static final AchievementDefinition INVOICE =
      new AchievementDefinition("invoice", "receipt", 10, List.of(level(1, BRONZE)));

  @Test
  void knows_the_contributed_achievements_in_the_order_of_the_profile_page() {
    var registry = new AchievementRegistry(List.of(contributor(TENURE), contributor(INVOICE)));

    assertThat(registry.all()).containsExactly(INVOICE, TENURE);
    assertThat(registry.isKnown("tenure", 5)).isTrue();
    assertThat(registry.isKnown("tenure", 2)).isFalse();
    assertThat(registry.isKnown("gone", 1)).isFalse();
  }

  @Test
  void the_same_key_from_two_modules_stops_the_start() {
    assertThatThrownBy(() -> new AchievementRegistry(List.of(contributor(TENURE), contributor(TENURE))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("tenure");
  }

  private static AchievementContributor contributor(AchievementDefinition definition) {
    return new AchievementContributor() {
      @Override
      public List<AchievementDefinition> getAchievements() {
        return List.of(definition);
      }

      @Override
      public List<ReachedAchievement> findReached(LocalDate today) {
        return List.of();
      }
    };
  }
}
