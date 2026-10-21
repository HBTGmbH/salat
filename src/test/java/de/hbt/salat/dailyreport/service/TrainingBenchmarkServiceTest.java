package de.hbt.salat.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.dailyreport.domain.TrainingInformation;
import de.hbt.salat.dailyreport.domain.WorkingDays;
import de.hbt.salat.dailyreport.persistence.TrainingRepository;
import de.hbt.salat.employee.domain.ContractWorkingTime;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.service.SpecialOrders;
import de.hbt.salat.common.test.FixedClock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * The median of the colleagues in the training account (#827): in working days over the period of
 * the own account, a colleague without training counting with none, and highlighted when it lies more
 * than the configured days above the own training.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
@FixedClock("2026-10-20T10:00:00")
class TrainingBenchmarkServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
  private static final LocalDate APRIL = LocalDate.of(2026, 4, 1);
  private static final Duration FULL_TIME = Duration.ofHours(8);

  private final EmployeecontractService employeecontractService = mock(EmployeecontractService.class);
  private final TrainingRepository trainingRepository = mock(TrainingRepository.class);
  private final SpecialOrders specialOrders = mock(SpecialOrders.class);
  private final SalatProperties salatProperties = new SalatProperties();
  private final List<ContractWorkingTime> contracts = new ArrayList<>();
  private final List<TrainingInformation> sums = new ArrayList<>();

  private final TrainingBenchmarkService service = new TrainingBenchmarkService(employeecontractService,
      trainingRepository, specialOrders, salatProperties);

  @BeforeEach
  void setUp() {
    when(specialOrders.getRegularTrainingSuborderIds()).thenReturn(Set.of(976L));
    when(employeecontractService.getComparableContractsCovering(any(), any(), any())).thenReturn(contracts);
    when(trainingRepository.sumRegularTrainingOfContractsBetween(anyCollection(), anyCollection(), any(), any()))
        .thenReturn(sums);
  }

  @Test
  void the_median_of_an_odd_number_is_the_middle_one_and_without_training_counts_as_none() {
    colleague(1, FULL_TIME, 0);
    colleague(2, FULL_TIME, 3 * 8);
    colleague(3, FULL_TIME, 6 * 8);

    assertThat(service.getBenchmark(APRIL, TODAY, days(0)).orElseThrow().medianDays()).isEqualByComparingTo("3");
  }

  @Test
  void the_median_of_an_even_number_is_the_mean_of_the_two_middle_ones() {
    colleague(1, FULL_TIME, 1 * 8);
    colleague(2, FULL_TIME, 2 * 8);
    colleague(3, FULL_TIME, 5 * 8);
    colleague(4, FULL_TIME, 9 * 8);

    assertThat(service.getBenchmark(APRIL, TODAY, days(0)).orElseThrow().medianDays()).isEqualByComparingTo("3.5");
  }

  @Test
  void a_colleague_part_time_counts_in_own_working_days() {
    colleague(1, Duration.ofHours(6), 12);

    assertThat(service.getBenchmark(APRIL, TODAY, days(0)).orElseThrow().medianDays()).isEqualByComparingTo("2");
  }

  @Test
  void more_than_four_days_above_the_own_training_is_highlighted_and_exactly_four_is_not() {
    colleague(1, FULL_TIME, 6 * 8);

    assertThat(service.getBenchmark(APRIL, TODAY, days(2)).orElseThrow().highlighted()).isFalse();
    assertThat(service.getBenchmark(APRIL, TODAY, WorkingDays.of(2 * 8 * 60 - 1, 8 * 60)).orElseThrow().highlighted())
        .isTrue();
  }

  @Test
  void the_threshold_comes_from_the_configuration() {
    salatProperties.getTraining().setMedianHighlightDays(1);
    colleague(1, FULL_TIME, 6 * 8);

    assertThat(service.getBenchmark(APRIL, TODAY, days(4)).orElseThrow().highlighted()).isTrue();
  }

  @Test
  void without_own_working_days_nothing_is_highlighted() {
    colleague(1, FULL_TIME, 10 * 8);

    var benchmark = service.getBenchmark(APRIL, TODAY, null).orElseThrow();

    assertThat(benchmark.medianDays()).isEqualByComparingTo("10");
    assertThat(benchmark.highlighted()).isFalse();
  }

  @Test
  void colleagues_and_their_training_are_read_over_the_period_cut_to_today() {
    colleague(1, FULL_TIME, 8);

    service.getBenchmark(APRIL, LocalDate.of(2026, 12, 31), days(0));

    verify(employeecontractService).getComparableContractsCovering(APRIL, TODAY, TrainingBenchmarkService.MINIMUM_DAILY_WORKING_TIME);
    verify(trainingRepository).sumRegularTrainingOfContractsBetween(List.of(1L), Set.of(976L), APRIL, TODAY);
  }

  @Test
  void twenty_hours_a_week_is_the_minimum() {
    assertThat(TrainingBenchmarkService.MINIMUM_DAILY_WORKING_TIME).isEqualTo(Duration.ofHours(4));
  }

  @Test
  void without_colleagues_or_with_a_period_in_the_future_there_is_no_benchmark() {
    assertThat(service.getBenchmark(APRIL, TODAY, days(0))).isEmpty();
    assertThat(service.getBenchmark(TODAY.plusDays(1), TODAY.plusDays(30), days(0))).isEmpty();
    verify(employeecontractService, never()).getComparableContractsCovering(eq(TODAY.plusDays(1)), any(), any());
  }

  private void colleague(long contractId, Duration dailyWorkingTime, int trainingHours) {
    contracts.add(new ContractWorkingTime(contractId, dailyWorkingTime));
    if (trainingHours > 0) {
      sums.add(new TrainingInformation(contractId, trainingHours * 60L));
    }
  }

  private static WorkingDays days(int days) {
    return WorkingDays.of(days * 8 * 60L, 8 * 60);
  }
}
