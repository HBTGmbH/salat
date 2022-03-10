package org.tb.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DateUtilsTest {

  @Test
  void sameDatesMustReturnWorkingDayDistance1IfWorkingDay() {
    LocalDate a = LocalDate.of(2022, 3, 1);
    LocalDate b = LocalDate.of(2022, 3, 1);
    assertThat(DateUtils.getWorkingDayDistance(a, b)).isEqualTo(1);
  }

  @Test
  void sameDatesMustReturnWorkingDayDistance0IfNotWorkingDay() {
    LocalDate a = LocalDate.of(2022, 3, 5);
    LocalDate b = LocalDate.of(2022, 3, 5);
    assertThat(DateUtils.getWorkingDayDistance(a, b)).isEqualTo(0);
  }

  @Test
  void workingDayDistanceMustConsiderWeekends() {
    LocalDate a = LocalDate.of(2022, 3, 1);
    LocalDate b = LocalDate.of(2022, 3, 11);
    assertThat(DateUtils.getWorkingDayDistance(a, b)).isEqualTo(9);
  }

}
