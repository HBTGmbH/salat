package de.hbt.salat.employee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.hbt.salat.common.achievement.ReachedAchievement;
import de.hbt.salat.employee.domain.EmploymentSpan;
import de.hbt.salat.employee.domain.Tenure;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/** Which levels of tenure are reached and when the next one comes (#1501). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class TenureAchievementContributorTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
  private static final long EMPLOYEE_ID = 7L;
  private static final Tenure SINCE_1997 =
      Tenure.of(List.of(new EmploymentSpan(EMPLOYEE_ID, LocalDate.of(2007, 1, 1), null)), LocalDate.of(1997, 1, 1));

  private final TenureService tenureService = mock(TenureService.class);
  private final TenureAchievementContributor contributor = new TenureAchievementContributor(tenureService);

  @Test
  void the_levels_go_up_to_fifty_years() {
    assertThat(TenureAchievementContributor.TENURE.getLevels()).containsExactly(1, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50);
  }

  @Test
  void every_level_reached_by_today_is_reported_with_its_day() {
    when(tenureService.getTenuresOfEmployeesEmployedOn(TODAY)).thenReturn(Map.of(EMPLOYEE_ID, SINCE_1997));

    assertThat(contributor.findReached(TODAY))
        .extracting(ReachedAchievement::level, ReachedAchievement::achievedOn)
        .containsExactly(
            tuple(1, LocalDate.of(1998, 1, 1)),
            tuple(5, LocalDate.of(2002, 1, 1)),
            tuple(10, LocalDate.of(2007, 1, 1)),
            tuple(15, LocalDate.of(2012, 1, 1)),
            tuple(20, LocalDate.of(2017, 1, 1)),
            tuple(25, LocalDate.of(2022, 1, 1)));
  }

  @Test
  void the_next_level_comes_on_its_anniversary() {
    when(tenureService.getTenure(EMPLOYEE_ID)).thenReturn(SINCE_1997);

    assertThat(contributor.forecast(EMPLOYEE_ID, TenureAchievementContributor.TENURE, 30, TODAY))
        .contains(LocalDate.of(2027, 1, 1));
  }
}
