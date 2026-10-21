package de.hbt.salat.dailyreport.service;

import static de.hbt.salat.common.util.DateUtils.today;
import static java.util.stream.Collectors.toMap;

import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.dailyreport.domain.TrainingBenchmark;
import de.hbt.salat.dailyreport.domain.TrainingInformation;
import de.hbt.salat.dailyreport.domain.WorkingDays;
import de.hbt.salat.dailyreport.persistence.TrainingRepository;
import de.hbt.salat.employee.domain.ContractWorkingTime;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.service.SpecialOrders;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The benchmark of the training account in „Meine Konten“ (#827): the median of the regular training
 * of the colleagues - the suborders of {@code salat.training.regular-suborder-signs}, resolved by
 * {@link SpecialOrders} -, in working days, so that the person sees where they stand. Only positive
 * incentives (#837): it is an aggregate over the whole company, no ranking, and no value of anybody
 * else leaves this service.
 *
 * <p>The colleagues are compared over exactly the period of the own account, cut to the validity of
 * the own contract and to today - a contract that began in April is compared from April on. Who takes
 * part is answered by {@link EmployeecontractService#getComparableContractsCovering}: a contract that
 * covers the whole period, so that nobody who started later pulls the median down, no freelancer
 * contract, at least 20 hours a week, and no restricted login (interns, externals). A colleague
 * without regular training counts with none.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Authorized
public class TrainingBenchmarkService {

  /** 20 hours a week, on five days: below that a contract is not compared. */
  static final Duration MINIMUM_DAILY_WORKING_TIME = Duration.ofHours(20).dividedBy(5);

  /** An id no suborder has: an empty IN list is not portable SQL. */
  private static final List<Long> NONE = List.of(-1L);

  private final EmployeecontractService employeecontractService;
  private final TrainingRepository trainingRepository;
  private final SpecialOrders specialOrders;
  private final SalatProperties salatProperties;

  /**
   * The benchmark for the own regular training {@code own} over the period; empty if the period lies
   * in the future or nobody takes part. Bookings after today never count.
   *
   * @param own the own regular training in working days, {@code null} where the own contract has no
   *            daily working time to count them in - then nothing is highlighted
   */
  public Optional<TrainingBenchmark> getBenchmark(LocalDate from, LocalDate until, WorkingDays own) {
    var today = today();
    var end = until.isAfter(today) ? today : until;
    if (from.isAfter(end)) {
      return Optional.empty();
    }
    // the contracts of other people, as ids and working time: only their median leaves this method
    var contracts = employeecontractService.getComparableContractsCovering(from, end, MINIMUM_DAILY_WORKING_TIME);
    if (contracts.isEmpty()) {
      return Optional.empty();
    }
    var regularTraining = specialOrders.getRegularTrainingSuborderIds();
    var minutes = trainingRepository.sumRegularTrainingOfContractsBetween(
            contracts.stream().map(ContractWorkingTime::employeecontractId).toList(),
            regularTraining.isEmpty() ? NONE : regularTraining, from, end).stream()
        .collect(toMap(TrainingInformation::getEmployeecontractId, TrainingInformation::getDurationMinutes));
    var days = contracts.stream()
        .map(contract -> WorkingDays.of(minutes.getOrDefault(contract.employeecontractId(), 0L),
            contract.dailyWorkingTime().toMinutes()).toDecimal())
        .sorted()
        .toList();
    var median = median(days);
    var threshold = BigDecimal.valueOf(salatProperties.getTraining().getMedianHighlightDays());
    boolean highlighted = own != null && median.subtract(own.toDecimal()).compareTo(threshold) > 0;
    return Optional.of(new TrainingBenchmark(median, highlighted));
  }

  /** The middle value of the sorted values, or the mean of the two middle ones. */
  static BigDecimal median(List<BigDecimal> sorted) {
    int middle = sorted.size() / 2;
    if (sorted.size() % 2 == 1) {
      return sorted.get(middle);
    }
    return sorted.get(middle - 1).add(sorted.get(middle)).divide(BigDecimal.TWO, 4, RoundingMode.HALF_UP);
  }
}
