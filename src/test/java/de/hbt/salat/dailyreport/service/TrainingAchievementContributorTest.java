package de.hbt.salat.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.hbt.salat.common.achievement.AchievementPerson;
import de.hbt.salat.common.achievement.AchievementProgress;
import de.hbt.salat.common.achievement.ReachedAchievement;
import de.hbt.salat.dailyreport.domain.TrainingDay;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/** Hours of training as levels (#1501): reached on the day the running sum crosses them. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class TrainingAchievementContributorTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
  private static final long ANNA = 1L;

  @Test
  void a_level_is_reached_on_the_day_the_sum_crosses_it() {
    var days = List.of(day("2024-03-01", 60 * 60), day("2024-06-01", 40 * 60), day("2025-01-10", 99 * 60),
        day("2025-02-01", 60));

    assertThat(TrainingAchievementContributor.reachedLevels(ANNA, days, TODAY))
        .extracting(ReachedAchievement::level, ReachedAchievement::achievedOn)
        .containsExactly(tuple(100, LocalDate.parse("2024-06-01")), tuple(200, LocalDate.parse("2025-02-01")));
  }

  @Test
  void one_day_may_cross_several_levels() {
    assertThat(TrainingAchievementContributor.reachedLevels(ANNA, List.of(day("2020-01-01", 520 * 60)), TODAY))
        .extracting(ReachedAchievement::level).containsExactly(100, 200, 500);
  }

  @Test
  void a_day_after_today_does_not_count() {
    assertThat(TrainingAchievementContributor.reachedLevels(ANNA, List.of(day("2026-10-21", 100 * 60)), TODAY)).isEmpty();
  }

  @Test
  void what_is_missing_is_rounded_up_to_whole_hours() {
    var service = mock(TrainingAchievementService.class);
    when(service.getTrainingMinutes(ANNA)).thenReturn(62L * 60 + 30);

    assertThat(new TrainingAchievementContributor(service)
        .progress(new AchievementPerson(ANNA, 11L), TrainingAchievementContributor.TRAINING, 100, TODAY))
        .contains(AchievementProgress.missing(38));
  }

  private static TrainingDay day(String day, long minutes) {
    return new TrainingDay(ANNA, LocalDate.parse(day), minutes);
  }
}
