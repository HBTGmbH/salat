package de.hbt.salat.dailyreport.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * Progress towards a target as the time account counts it (#1469): booked time and time surcharges together, the
 * bar in two pieces that never run beyond the whole.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class TargetProgressTest {

  @Test
  void the_percent_counts_booked_time_and_surcharges() {
    var progress = new TargetProgress(Duration.ofHours(6), Duration.ofHours(1), Duration.ofHours(8));

    assertThat(progress.percent()).isEqualTo(87);
    assertThat(progress.bookedBar()).isEqualTo(75.0);
    assertThat(progress.surchargeBar()).isEqualTo(12.5);
    assertThat(progress.remaining()).isEqualTo(Duration.ofHours(1));
  }

  @Test
  void beyond_the_target_the_bar_stays_whole_and_the_percent_goes_on() {
    var progress = new TargetProgress(Duration.ofHours(7), Duration.ofHours(3), Duration.ofHours(8));

    assertThat(progress.percent()).isEqualTo(125);
    assertThat(progress.bookedBar()).isEqualTo(87.5);
    assertThat(progress.surchargeBar()).isEqualTo(12.5);
    assertThat(progress.remaining()).isEqualTo(Duration.ofHours(-2));
  }

  @Test
  void without_a_target_everything_is_zero() {
    var progress = new TargetProgress(Duration.ofHours(3), Duration.ofHours(3), Duration.ZERO);

    assertThat(progress.percent()).isZero();
    assertThat(progress.bookedBar()).isZero();
    assertThat(progress.surchargeBar()).isZero();
  }

  /* An hour of credit on a month of 121 hours is less than one percent, and must not round away. */
  @Test
  void a_small_credit_keeps_a_visible_piece() {
    assertThat(new TargetProgress(Duration.ofHours(42), Duration.ofHours(1), Duration.ofHours(121)).surchargeBar())
        .isEqualTo(0.8);
  }
}
