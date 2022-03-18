package org.tb.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DateUtilsTest {

  @Test
  void same_dates_must_return_working_day_distance1_if_working_day() {
    LocalDate a = LocalDate.of(2022, 3, 1);
    LocalDate b = LocalDate.of(2022, 3, 1);
    assertThat(DateUtils.getWorkingDayDistance(a, b)).isEqualTo(1);
  }

  @Test
  void same_dates_must_return_working_day_distance0_if_not_working_day() {
    LocalDate a = LocalDate.of(2022, 3, 5);
    LocalDate b = LocalDate.of(2022, 3, 5);
    assertThat(DateUtils.getWorkingDayDistance(a, b)).isEqualTo(0);
  }

  @Test
  void working_day_distance_must_consider_weekends() {
    LocalDate a = LocalDate.of(2022, 3, 1);
    LocalDate b = LocalDate.of(2022, 3, 11);
    assertThat(DateUtils.getWorkingDayDistance(a, b)).isEqualTo(9);
  }

}
