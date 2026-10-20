package de.hbt.salat.achievement.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.hbt.salat.achievement.service.AchievementService;
import java.time.LocalDate;
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

  private final AchievementViewHelper viewHelper = new AchievementViewHelper(mock(AchievementService.class), messages());

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

  private static MessageSourceAccessor messages() {
    var source = new ResourceBundleMessageSource();
    source.setBasename("de.hbt.salat.web.MessageResources");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    return new MessageSourceAccessor(source, Locale.GERMAN);
  }
}
