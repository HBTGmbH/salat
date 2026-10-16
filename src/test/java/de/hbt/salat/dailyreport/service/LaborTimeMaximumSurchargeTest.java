package de.hbt.salat.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.SurchargeKind;

/**
 * The legal maximum of ten hours a day counts the time worked only (#1469): a time surcharge is a credit on the
 * account, not time worked - neither in the daily view and the matrix nor in the check before the release.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class LaborTimeMaximumSurchargeTest {

  private static final LocalDate DAY = LocalDate.of(2026, 10, 14);

  @InjectMocks
  private WorkingdayService workingdayService;

  @Test
  void nine_hours_with_two_hours_of_credit_stay_within_the_maximum() {
    var day = List.of(booking(Duration.ofHours(9), Duration.ofHours(2)));

    assertThat(workingdayService.checkLaborTimeMaximum(day)).isFalse();
    assertThat(ReleaseService.validateWorkingDayLength(DAY, new HashMap<>(Map.of(DAY, day)))).isEmpty();
  }

  @Test
  void more_than_ten_hours_worked_exceed_it_with_or_without_credit() {
    var day = List.of(booking(Duration.ofHours(10).plusMinutes(1), Duration.ZERO));

    assertThat(workingdayService.checkLaborTimeMaximum(day)).isTrue();
    assertThat(ReleaseService.validateWorkingDayLength(DAY, new HashMap<>(Map.of(DAY, day)))).isPresent();
  }

  private static TimereportDTO booking(Duration duration, Duration credit) {
    return TimereportDTO.builder().referenceday(DAY).orderType(OrderType.STANDARD).duration(duration)
        .surchargeKind(credit.isZero() ? null : SurchargeKind.NIGHT).surchargePercent(credit.isZero() ? null : 25)
        .surchargeCredit(credit).build();
  }
}
