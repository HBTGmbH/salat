package de.hbt.salat.employee.service;

import static de.hbt.salat.employee.service.TenureAchievementContributor.FIRST_100_DAYS;
import static de.hbt.salat.employee.service.TenureAchievementContributor.TENURE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.hbt.salat.common.achievement.AchievementPerson;
import de.hbt.salat.common.achievement.AchievementProgress;
import de.hbt.salat.common.achievement.AchievementTier;
import de.hbt.salat.common.achievement.ReachedAchievement;
import de.hbt.salat.employee.domain.EmploymentSpan;
import de.hbt.salat.employee.domain.Tenure;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/** Which levels of time with the company are reached and when the next one comes (#1501). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class TenureAchievementContributorTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
  private static final long VETERAN = 7L;
  private static final long NEWCOMER = 8L;
  private static final Tenure SINCE_1997 =
      Tenure.of(List.of(new EmploymentSpan(VETERAN, LocalDate.of(2007, 1, 1), null)), LocalDate.of(1997, 1, 1));
  private static final Tenure SINCE_SEPTEMBER =
      Tenure.of(List.of(new EmploymentSpan(NEWCOMER, LocalDate.of(2026, 9, 1), null)), null);

  private final TenureService tenureService = mock(TenureService.class);
  private final TenureAchievementContributor contributor = new TenureAchievementContributor(tenureService);

  @Test
  void loyalist_climbs_from_bronze_to_grand_master_over_fifty_years() {
    assertThat(TENURE.getLevels()).containsExactly(1, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50);
    assertThat(TENURE.tierOf(1)).isEqualTo(AchievementTier.BRONZE);
    assertThat(TENURE.tierOf(25)).isEqualTo(AchievementTier.MASTER);
    assertThat(TENURE.tierOf(50)).isEqualTo(AchievementTier.GRANDMASTER);
  }

  @Test
  void every_level_reached_by_today_is_reported_with_its_day() {
    when(tenureService.getTenuresOfEmployeesEmployedOn(TODAY)).thenReturn(Map.of(VETERAN, SINCE_1997));

    assertThat(contributor.findReached(TODAY))
        .extracting(ReachedAchievement::key, ReachedAchievement::level, ReachedAchievement::achievedOn)
        .containsExactly(
            tuple("first-100-days", 100, LocalDate.of(1997, 4, 10)),
            tuple("tenure", 1, LocalDate.of(1998, 1, 1)),
            tuple("tenure", 5, LocalDate.of(2002, 1, 1)),
            tuple("tenure", 10, LocalDate.of(2007, 1, 1)),
            tuple("tenure", 15, LocalDate.of(2012, 1, 1)),
            tuple("tenure", 20, LocalDate.of(2017, 1, 1)),
            tuple("tenure", 25, LocalDate.of(2022, 1, 1)));
  }

  @Test
  void the_first_100_days_end_on_the_hundredth_day() {
    when(tenureService.getTenure(NEWCOMER)).thenReturn(SINCE_SEPTEMBER);

    assertThat(contributor.progress(new AchievementPerson(NEWCOMER, 18L), FIRST_100_DAYS, 100, TODAY))
        .contains(AchievementProgress.on(LocalDate.of(2026, 12, 9)));
  }

  @Test
  void the_next_level_comes_on_its_anniversary() {
    when(tenureService.getTenure(VETERAN)).thenReturn(SINCE_1997);

    assertThat(contributor.progress(new AchievementPerson(VETERAN, 17L), TENURE, 30, TODAY))
        .contains(AchievementProgress.on(LocalDate.of(2027, 1, 1)));
  }
}
