package de.hbt.salat.achievement.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import de.hbt.salat.achievement.domain.AchievementQuote;
import de.hbt.salat.beta.service.BetaAchievementContributor;
import de.hbt.salat.common.achievement.AchievementDefinition;
import de.hbt.salat.dailyreport.service.TrainingAchievementContributor;
import de.hbt.salat.dailyreport.service.TrainingYearAchievementContributor;
import de.hbt.salat.employee.service.TenureAchievementContributor;
import de.hbt.salat.palette.service.PaletteAchievementContributor;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/** Every level of every achievement has its quotes, three to five (#1501). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class AchievementQuotesTest {

  private static final List<AchievementDefinition> ACHIEVEMENTS = List.of(TenureAchievementContributor.FIRST_100_DAYS,
      TenureAchievementContributor.TENURE, TrainingAchievementContributor.TRAINING, PaletteAchievementContributor.PALETTE,
      BetaAchievementContributor.BETA, TrainingYearAchievementContributor.TRAINING_YEAR);

  private final AchievementQuotes quotes = new AchievementQuotes();

  @Test
  void every_level_has_three_to_five_different_quotes_with_their_source() {
    for (var achievement : ACHIEVEMENTS) {
      for (int level : achievement.getLevels()) {
        var ofLevel = quotes.of(achievement.getKey(), level);
        assertThat(ofLevel).as(achievement + " " + level).hasSizeBetween(3, 5).doesNotHaveDuplicates()
            .allSatisfy(quote -> {
              assertThat(quote.quote()).isNotBlank();
              assertThat(quote.source()).isNotBlank();
            });
      }
    }
  }

  @Test
  void a_random_quote_is_one_of_the_level() {
    var ofLevel = quotes.of("tenure", 25);

    assertThat(quotes.random("tenure", 25)).get().isIn(ofLevel);
  }

  @Test
  void a_level_without_quotes_has_none() {
    assertThat(quotes.random("gone", 1)).isEmpty();
    assertThat(quotes.of("tenure", 2)).isEmpty();
  }

  @Test
  void no_quote_stands_twice_in_the_whole_collection() {
    var all = ACHIEVEMENTS.stream()
        .flatMap(achievement -> achievement.getLevels().stream().flatMap(level -> quotes.of(achievement.getKey(), level).stream()))
        .map(AchievementQuote::quote)
        .toList();
    assertThat(all).doesNotHaveDuplicates();
  }
}
