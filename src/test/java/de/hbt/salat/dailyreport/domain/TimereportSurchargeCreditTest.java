package de.hbt.salat.dailyreport.domain;

import static de.hbt.salat.order.domain.SurchargeKind.NIGHT;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/** The time surcharge credit of a booking (#1469): duration × rate in whole minutes, following the duration. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportSurchargeCreditTest {

  @Test
  void the_credit_is_duration_times_rate() {
    assertThat(Timereport.surchargeCreditFor(Duration.ofHours(4), 25)).isEqualTo(Duration.ofHours(1));
    assertThat(Timereport.surchargeCreditFor(Duration.ofHours(3), 50)).isEqualTo(Duration.ofMinutes(90));
    assertThat(Timereport.surchargeCreditFor(Duration.ofMinutes(75), 100)).isEqualTo(Duration.ofMinutes(75));
  }

  @Test
  void the_credit_is_rounded_to_whole_minutes() {
    // 1:30 × 25 % = 22,5 minutes
    assertThat(Timereport.surchargeCreditFor(Duration.ofMinutes(90), 25)).isEqualTo(Duration.ofMinutes(23));
    // 0:10 × 33 % = 3,3 minutes
    assertThat(Timereport.surchargeCreditFor(Duration.ofMinutes(10), 33)).isEqualTo(Duration.ofMinutes(3));
  }

  @Test
  void the_credit_follows_the_duration_and_goes_with_the_flag() {
    var timereport = new Timereport();
    timereport.setDuration(Duration.ofHours(4));
    timereport.setSurcharge(NIGHT, 25);
    assertThat(timereport.getSurchargeCredit()).isEqualTo(Duration.ofHours(1));

    timereport.setDuration(Duration.ofHours(2));
    assertThat(timereport.getSurchargeCredit()).isEqualTo(Duration.ofMinutes(30));

    timereport.setSurcharge(null, 25);
    assertThat(timereport.getSurchargeCredit()).isZero();
    assertThat(timereport.getSurchargePercent()).isNull();
  }
}
