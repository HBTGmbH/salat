package de.hbt.salat.beta.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import de.hbt.salat.beta.domain.AnsweredBeta;
import de.hbt.salat.common.achievement.ReachedAchievement;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/** Betas answered as levels (#1501): the n-th answer reaches level n on its day. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class BetaAchievementContributorTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
  private static final long ANNA = 1L;

  @Test
  void the_third_answer_reaches_level_three_on_its_day() {
    var answered = List.of(answer("a", "2026-01-10"), answer("b", "2026-03-02"), answer("c", "2026-07-15"));

    assertThat(BetaAchievementContributor.reachedLevels(ANNA, answered, TODAY))
        .extracting(ReachedAchievement::level, ReachedAchievement::achievedOn)
        .containsExactly(tuple(1, LocalDate.parse("2026-01-10")), tuple(3, LocalDate.parse("2026-07-15")));
  }

  @Test
  void without_an_answer_nothing_is_reached() {
    assertThat(BetaAchievementContributor.reachedLevels(ANNA, List.of(), TODAY)).isEmpty();
  }

  private static AnsweredBeta answer(String feature, String day) {
    return new AnsweredBeta(ANNA, feature, LocalDate.parse(day));
  }
}
