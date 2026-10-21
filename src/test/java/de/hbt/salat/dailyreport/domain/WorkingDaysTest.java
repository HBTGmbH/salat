package de.hbt.salat.dailyreport.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/** Booked time in working days (#1512): exact, whatever the daily working time of the contracts. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class WorkingDaysTest {

  @Test
  void a_working_day_is_the_daily_working_time_of_the_contract() {
    assertThat(WorkingDays.of(16 * 60, 8 * 60).toDecimal()).isEqualByComparingTo("2");
    assertThat(WorkingDays.of(6 * 60, 6 * 60).reaches(1)).isTrue();
  }

  /* A decimal sum of 15 half hours on a day of 7:30 lands just below one day; the fraction does not. */
  @Test
  void many_small_bookings_add_up_to_a_full_day_exactly() {
    var sum = WorkingDays.ZERO;
    for (int i = 0; i < 15; i++) {
      sum = sum.plus(30, 450);
    }

    assertThat(sum.reaches(1)).isTrue();
    assertThat(sum.reaches(2)).isFalse();
    assertThat(sum.missingTo(1)).isEqualByComparingTo("0");
  }

  @Test
  void contracts_with_different_working_times_add_up() {
    var sum = WorkingDays.of(4 * 60, 8 * 60).plus(3 * 60, 6 * 60);

    assertThat(sum.reaches(1)).isTrue();
    assertThat(sum.toDecimal()).isEqualByComparingTo("1");
  }

  @Test
  void what_is_missing_is_rounded_up_to_a_tenth() {
    assertThat(WorkingDays.of(60, 8 * 60).missingTo(1)).isEqualTo(new BigDecimal("0.9"));
    assertThat(WorkingDays.of(61, 8 * 60).missingTo(1)).isEqualTo(new BigDecimal("0.9"));
    assertThat(WorkingDays.of(1, 8 * 60).missingTo(2)).isEqualTo(new BigDecimal("2.0"));
  }

  @Test
  void a_contract_without_daily_working_time_has_no_working_days() {
    assertThatThrownBy(() -> WorkingDays.of(60, 0)).isInstanceOf(IllegalArgumentException.class);
  }
}
