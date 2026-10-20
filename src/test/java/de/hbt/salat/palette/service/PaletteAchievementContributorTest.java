package de.hbt.salat.palette.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.hbt.salat.common.achievement.AchievementPerson;
import de.hbt.salat.common.achievement.AchievementProgress;
import de.hbt.salat.common.achievement.ReachedAchievement;
import de.hbt.salat.palette.domain.PaletteUseDay;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/** Runs of the palette as levels (#1501), reported by login: the palette knows no employees. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class PaletteAchievementContributorTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
  private static final long LOGIN = 11L;

  @Test
  void a_level_is_reached_by_login_on_the_day_the_sum_crosses_it() {
    var days = List.of(new PaletteUseDay(LOGIN, LocalDate.parse("2026-10-01"), 60),
        new PaletteUseDay(LOGIN, LocalDate.parse("2026-10-02"), 45));

    assertThat(PaletteAchievementContributor.reachedLevels(LOGIN, days, TODAY))
        .extracting(ReachedAchievement::salatUserId, ReachedAchievement::employeeId, ReachedAchievement::level,
            ReachedAchievement::achievedOn)
        .containsExactly(tuple(LOGIN, null, 100, LocalDate.parse("2026-10-02")));
  }

  @Test
  void the_next_level_says_how_many_runs_are_missing() {
    var service = mock(PaletteUseService.class);
    when(service.getUses(LOGIN)).thenReturn(463L);

    assertThat(new PaletteAchievementContributor(service)
        .progress(new AchievementPerson(1L, LOGIN), PaletteAchievementContributor.PALETTE, 500, TODAY))
        .contains(AchievementProgress.missing(37));
  }
}
