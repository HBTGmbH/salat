package de.hbt.salat.achievement.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.hbt.salat.achievement.domain.AchievementProfile;
import de.hbt.salat.achievement.service.AchievementService;
import de.hbt.salat.common.achievement.AchievementDefinition;
import de.hbt.salat.common.achievement.AchievementLevel;
import de.hbt.salat.common.achievement.AchievementTier;
import de.hbt.salat.dailyreport.service.TrainingAchievementContributor;
import de.hbt.salat.dailyreport.service.TrainingYearAchievementContributor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;

/** The time until the next level on the profile page (#1501), with the real German texts. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class AchievementViewHelperTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

  private final AchievementViewHelper viewHelper = new AchievementViewHelper(mock(AchievementService.class), messages(), new AchievementQuotes());

  @Test
  void reached_types_follow_their_position_then_key_and_keep_the_chronological_medal_order() {
    var first = definition("z-first", 10);
    var second = definition("a-second", 20);
    var third = definition("b-third", 20);
    var newest = reached(second, 1, "2026-06-01");
    var higher = reached(first, 5, "2026-05-01");
    var lower = reached(first, 1, "2026-05-01");
    var other = reached(third, 1, "2026-04-01");
    var oldest = reached(second, 5, "2025-01-01");

    var groups = viewHelper.reachedGroups(List.of(newest, higher, lower, other, oldest));

    assertThat(groups).extracting(AchievementViewHelper.ReachedGroup::definition).containsExactly(first, second, third);
    assertThat(groups.get(0).items()).containsExactly(higher, lower);
    assertThat(groups.get(1).items()).containsExactly(newest, oldest);
    assertThat(groups.get(2).items()).containsExactly(other);
  }

  @Test
  void yearly_medals_from_different_years_share_one_type_in_their_existing_order() {
    var yearly = TrainingYearAchievementContributor.TRAINING_YEAR;
    var current = reached(yearly, 2, "2026-05-01");
    var previous = reached(yearly, 4, "2025-04-01");

    assertThat(viewHelper.reachedGroups(List.of(current, previous)))
        .containsExactly(new AchievementViewHelper.ReachedGroup(yearly, List.of(current, previous)));
  }

  @Test
  void no_reached_medals_means_no_type_groups() {
    assertThat(viewHelper.reachedGroups(List.of())).isEmpty();
  }

  @Test
  void dialog_ids_distinguish_types_levels_and_years() {
    var yearly = TrainingYearAchievementContributor.TRAINING_YEAR;

    assertThat(List.of(
        viewHelper.reachedModalId(reached(yearly, 1, "2026-05-01")),
        viewHelper.reachedModalId(reached(yearly, 2, "2026-05-01")),
        viewHelper.reachedModalId(reached(yearly, 1, "2025-05-01")),
        viewHelper.reachedModalId(reached(definition("other", 10), 1, "2026-05-01"))))
        .doesNotHaveDuplicates().allMatch(id -> id.matches("[a-zA-Z][a-zA-Z0-9-]*"));
    assertThat(viewHelper.reachedModalId(reached(yearly, 1, "2026-05-01")))
        .isEqualTo("achievementReached-training-year-1-2026-05-01");
  }

  @Test
  void years_and_months() {
    assertThat(viewHelper.remaining(TODAY, LocalDate.of(2031, 3, 1))).isEqualTo("noch 4 Jahre 4 Monate");
  }

  @Test
  void one_year_and_one_month_in_the_singular() {
    assertThat(viewHelper.remaining(TODAY, LocalDate.of(2027, 11, 20))).isEqualTo("noch 1 Jahr 1 Monat");
  }

  @Test
  void years_or_months_alone() {
    assertThat(viewHelper.remaining(TODAY, LocalDate.of(2028, 10, 25))).isEqualTo("noch 2 Jahre");
    assertThat(viewHelper.remaining(TODAY, LocalDate.of(2027, 1, 20))).isEqualTo("noch 3 Monate");
  }

  @Test
  void less_than_a_month() {
    assertThat(viewHelper.remaining(TODAY, LocalDate.of(2026, 11, 3))).isEqualTo("in weniger als einem Monat");
  }

  @Test
  void the_plate_shortens_thousands() {
    LocaleContextHolder.setLocale(Locale.GERMAN);
    try {
      assertThat(viewHelper.plate(25)).isEqualTo("25");
      assertThat(viewHelper.plate(500)).isEqualTo("500");
      assertThat(viewHelper.plate(1000)).isEqualTo("1,0K");
      assertThat(viewHelper.plate(2500)).isEqualTo("2,5K");
      assertThat(viewHelper.plate(10000)).isEqualTo("10K");
    } finally {
      LocaleContextHolder.resetLocaleContext();
    }
  }

  @Test
  void the_plate_follows_the_reader_s_language() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    try {
      assertThat(viewHelper.plate(2500)).isEqualTo("2.5K");
    } finally {
      LocaleContextHolder.resetLocaleContext();
    }
  }

  /* #1512: working days of training are missing with a decimal, one day in the singular. */
  @Test
  void working_days_of_training_still_missing_with_a_decimal() {
    assertThat(missing("1.5")).isEqualTo("noch 1,5 Tage");
    assertThat(missing("1.0")).isEqualTo("noch 1 Tag");
    assertThat(missing("0.5")).isEqualTo("noch 0,5 Tage");
    assertThat(missing("2.0")).isEqualTo("noch 2 Tage");
  }

  /* #1512: a yearly medal carries its year on the plate; the days stand in the text below it. */
  @Test
  void the_plate_of_a_yearly_achievement_shows_the_year() {
    assertThat(viewHelper.plate(TrainingYearAchievementContributor.TRAINING_YEAR, 4, LocalDate.of(2025, 11, 3)))
        .isEqualTo("2025");
  }

  @Test
  void the_plate_of_any_other_achievement_shows_the_level() {
    assertThat(viewHelper.plate(TrainingAchievementContributor.TRAINING, 500, LocalDate.of(2025, 11, 3))).isEqualTo("500");
  }

  private String missing(String days) {
    var next = new AchievementProfile.Next(TrainingYearAchievementContributor.TRAINING_YEAR, 4, null, new BigDecimal(days));
    return viewHelper.next(next, TODAY);
  }

  private static AchievementDefinition definition(String key, int position) {
    return new AchievementDefinition(key, "flag", position,
        List.of(new AchievementLevel(1, AchievementTier.BRONZE), new AchievementLevel(5, AchievementTier.SILVER)));
  }

  private static AchievementProfile.Reached reached(AchievementDefinition definition, int level, String day) {
    return new AchievementProfile.Reached(definition, level, definition.tierOf(level), LocalDate.parse(day));
  }

  private static MessageSourceAccessor messages() {
    var source = new ResourceBundleMessageSource();
    source.setBasename("de.hbt.salat.web.MessageResources");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    return new MessageSourceAccessor(source, Locale.GERMAN);
  }
}
