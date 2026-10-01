package de.hbt.salat.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import de.hbt.salat.dailyreport.auth.TimereportAuthorization;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.domain.OrderType;

/**
 * Der Rest des Tagessolls, mit dem das Buchungsformular eine Abwesenheit vorbelegt (#1214): das
 * Tagessoll abzüglich der an diesem Tag schon gebuchten Arbeitszeit, nie negativ. Ob der Tag ein
 * Soll hat — Wochenende, Feiertag, Vertrag ohne Tagesarbeitszeit —, entscheidet
 * {@link OvertimeService#calculateWorkingTimeTarget}, das hier für den einen Tag gefragt wird (#857).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class DailyServiceRemainingDayTargetTest {

  private static final long CONTRACT_ID = 42L;
  private static final LocalDate DAY = LocalDate.of(2026, 5, 19);

  @Mock
  private TimereportService timereportService;
  @Mock
  private WorkingdayService workingdayService;
  @Mock
  private PublicholidayService publicholidayService;
  @Mock
  private OvertimeService overtimeService;
  @Mock
  private EmployeecontractService employeecontractService;
  @Mock
  private TimereportAuthorization timereportAuthorization;

  private DailyService dailyService;

  @BeforeEach
  void setUp() {
    dailyService = new DailyService(timereportService, workingdayService, publicholidayService, overtimeService,
        employeecontractService, timereportAuthorization);
    target(Duration.ofHours(8));
    booked();
  }

  @Test
  void a_day_without_bookings_leaves_the_whole_target() {
    assertThat(dailyService.getRemainingDayTarget(DAY, CONTRACT_ID)).isEqualTo(Duration.ofHours(8));
  }

  @Test
  void a_partly_booked_day_leaves_what_is_not_booked_yet() {
    booked(booking(Duration.ofMinutes(2 * 60 + 15), OrderType.STANDARD),
        booking(Duration.ofHours(1), OrderType.STANDARD));

    assertThat(dailyService.getRemainingDayTarget(DAY, CONTRACT_ID)).isEqualTo(Duration.ofMinutes(4 * 60 + 45));
  }

  @Test
  void a_day_booked_to_the_target_leaves_nothing() {
    booked(booking(Duration.ofHours(8), OrderType.STANDARD));

    assertThat(dailyService.getRemainingDayTarget(DAY, CONTRACT_ID)).isZero();
  }

  @Test
  void a_day_booked_beyond_the_target_leaves_nothing_rather_than_a_negative_rest() {
    booked(booking(Duration.ofHours(9), OrderType.STANDARD));

    assertThat(dailyService.getRemainingDayTarget(DAY, CONTRACT_ID)).isZero();
  }

  @Test
  void standby_is_no_working_time_and_leaves_the_rest_untouched() {
    booked(booking(Duration.ofHours(3), OrderType.STANDARD), booking(Duration.ofHours(6), OrderType.BEREITSCHAFT));

    assertThat(dailyService.getRemainingDayTarget(DAY, CONTRACT_ID)).isEqualTo(Duration.ofHours(5));
  }

  @Test
  void an_absence_already_booked_counts_like_any_other_working_time() {
    booked(booking(Duration.ofHours(4), OrderType.KRANK_URLAUB_ABWESEND));

    assertThat(dailyService.getRemainingDayTarget(DAY, CONTRACT_ID)).isEqualTo(Duration.ofHours(4));
  }

  /** Ein Vertrag ohne Tagesarbeitszeit, ein Wochenende oder ein Feiertag: der Tag hat kein Soll. */
  @Test
  void a_day_without_target_leaves_nothing() {
    target(Duration.ZERO);

    assertThat(dailyService.getRemainingDayTarget(DAY, CONTRACT_ID)).isZero();
  }

  @Test
  void a_day_without_target_leaves_nothing_even_with_standby_only() {
    target(Duration.ZERO);
    booked(booking(Duration.ofHours(2), OrderType.BEREITSCHAFT));

    assertThat(dailyService.getRemainingDayTarget(DAY, CONTRACT_ID)).isZero();
  }

  private void target(Duration target) {
    when(overtimeService.calculateWorkingTimeTarget(CONTRACT_ID, DAY, DAY)).thenReturn(target);
  }

  private void booked(TimereportDTO... bookings) {
    when(timereportService.getTimereportsByDateAndEmployeeContractId(CONTRACT_ID, DAY)).thenReturn(List.of(bookings));
  }

  private static TimereportDTO booking(Duration duration, OrderType orderType) {
    return TimereportDTO.builder()
        .employeecontractId(CONTRACT_ID)
        .referenceday(DAY)
        .status("open")
        .orderType(orderType)
        .duration(duration)
        .build();
  }
}
