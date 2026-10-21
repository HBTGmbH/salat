package de.hbt.salat.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.hbt.salat.common.achievement.AchievementPerson;
import de.hbt.salat.common.achievement.AchievementProgress;
import de.hbt.salat.common.achievement.ReachedAchievement;
import de.hbt.salat.dailyreport.domain.RegularTrainingDay;
import de.hbt.salat.dailyreport.domain.WorkingDays;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * Working days of regular training in a calendar year as levels (#1512): reached on the day the
 * year's sum crosses them, and every year starts anew.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class TrainingYearAchievementContributorTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
  private static final long ANNA = 1L;
  private static final Duration FULL_TIME = Duration.ofHours(8);

  @Test
  void the_levels_are_one_two_four_six_eight_and_ten_days_from_bronze_to_master() {
    assertThat(TrainingYearAchievementContributor.TRAINING_YEAR.getLevels()).containsExactly(1, 2, 4, 6, 8, 10);
    assertThat(TrainingYearAchievementContributor.TRAINING_YEAR.isYearly()).isTrue();
  }

  @Test
  void a_level_is_reached_on_the_day_the_working_days_of_the_year_cross_it() {
    var days = List.of(day("2026-02-02", 4), day("2026-02-03", 4), day("2026-05-04", 7), day("2026-05-05", 1));

    assertThat(reached(days)).extracting(ReachedAchievement::level, ReachedAchievement::achievedOn)
        .containsExactly(tuple(1, LocalDate.parse("2026-02-03")), tuple(2, LocalDate.parse("2026-05-05")));
  }

  @Test
  void every_year_starts_anew() {
    var days = List.of(day("2025-03-02", 16), day("2026-01-12", 4), day("2026-01-13", 4));

    assertThat(reached(days)).extracting(ReachedAchievement::level, ReachedAchievement::achievedOn)
        .containsExactly(tuple(1, LocalDate.parse("2025-03-02")), tuple(2, LocalDate.parse("2025-03-02")),
            tuple(1, LocalDate.parse("2026-01-13")));
  }

  /* A December booking entered in January belongs to December: the job finds it with its own day. */
  @Test
  void a_booking_counts_for_the_year_of_its_day() {
    var days = List.of(day("2025-12-15", 4), day("2025-12-16", 4), day("2026-01-05", 4));

    assertThat(reached(days)).extracting(ReachedAchievement::level, ReachedAchievement::achievedOn)
        .containsExactly(tuple(1, LocalDate.parse("2025-12-16")));
  }

  @Test
  void a_working_day_of_a_part_time_contract_is_its_own_daily_working_time() {
    var days = List.of(day("2026-03-02", 6, Duration.ofHours(6)));

    assertThat(reached(days)).extracting(ReachedAchievement::level).containsExactly(1);
  }

  /* Four hours on a full-time contract and three on a part-time one of six hours are one working day. */
  @Test
  void the_working_days_of_all_contracts_of_the_year_add_up() {
    var days = List.of(day("2026-03-02", 4, FULL_TIME), day("2026-08-03", 3, Duration.ofHours(6)));

    assertThat(reached(days)).extracting(ReachedAchievement::level, ReachedAchievement::achievedOn)
        .containsExactly(tuple(1, LocalDate.parse("2026-08-03")));
  }

  @Test
  void ten_days_is_the_top_and_booking_more_brings_nothing() {
    assertThat(reached(List.of(day("2026-03-02", 8 * 14)))).extracting(ReachedAchievement::level)
        .containsExactly(1, 2, 4, 6, 8, 10);
  }

  @Test
  void a_day_after_today_does_not_count() {
    assertThat(reached(List.of(day("2026-10-21", 8)))).isEmpty();
  }

  @Test
  void what_is_missing_is_counted_in_the_current_year_with_a_decimal() {
    var service = mock(TrainingAchievementService.class);
    when(service.getRegularTrainingWorkingDaysOfYear(ANNA, TODAY)).thenReturn(WorkingDays.of(2 * 8 * 60 + 4 * 60, 8 * 60));

    assertThat(new TrainingYearAchievementContributor(service)
        .progress(new AchievementPerson(ANNA, 11L), TrainingYearAchievementContributor.TRAINING_YEAR, 4, TODAY))
        .contains(AchievementProgress.missing(new BigDecimal("1.5")));
  }

  @Test
  void a_level_reached_today_but_not_yet_recorded_still_shows_a_tenth_missing() {
    var service = mock(TrainingAchievementService.class);
    when(service.getRegularTrainingWorkingDaysOfYear(ANNA, TODAY)).thenReturn(WorkingDays.of(8 * 60, 8 * 60));

    assertThat(new TrainingYearAchievementContributor(service)
        .progress(new AchievementPerson(ANNA, 11L), TrainingYearAchievementContributor.TRAINING_YEAR, 1, TODAY))
        .contains(AchievementProgress.missing(new BigDecimal("0.1")));
  }

  private static List<ReachedAchievement> reached(List<RegularTrainingDay> days) {
    return TrainingYearAchievementContributor.reachedLevels(ANNA, days, TODAY);
  }

  private static RegularTrainingDay day(String day, int hours) {
    return day(day, hours, FULL_TIME);
  }

  private static RegularTrainingDay day(String day, int hours, Duration dailyWorkingTime) {
    return new RegularTrainingDay(ANNA, LocalDate.parse(day), hours * 60L, dailyWorkingTime);
  }
}
