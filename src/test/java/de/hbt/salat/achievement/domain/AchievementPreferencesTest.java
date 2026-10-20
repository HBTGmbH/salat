package de.hbt.salat.achievement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class AchievementPreferencesTest {

  @Test
  void announcements_are_on_unless_switched_off() {
    assertThat(AchievementPreferences.from(null).announce()).isTrue();
    assertThat(AchievementPreferences.from(Map.of()).announce()).isTrue();
    assertThat(AchievementPreferences.from(Map.of("announce", "no")).announce()).isTrue();
    assertThat(AchievementPreferences.from(Map.of("announce", false)).announce()).isFalse();
  }

  @Test
  void only_the_switched_off_state_is_stored() {
    assertThat(AchievementPreferences.defaults().toMap()).isEmpty();
    assertThat(new AchievementPreferences(false).toMap()).isEqualTo(Map.of("announce", false));
    assertThat(AchievementPreferences.from(new AchievementPreferences(false).toMap()).announce()).isFalse();
  }
}
