package de.hbt.salat.dailyreport.service;

import static de.hbt.salat.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static java.util.stream.Collectors.groupingBy;

import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.dailyreport.domain.RegularTrainingDay;
import de.hbt.salat.dailyreport.domain.TrainingDay;
import de.hbt.salat.dailyreport.domain.WorkingDays;
import de.hbt.salat.dailyreport.persistence.TrainingRepository;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.order.service.SpecialOrders;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The training people booked, for the achievement „Fortbildung“ (#1501): bookings flagged as
 * training and bookings on a suborder of the regular training - the rule of the training account in
 * „Meine Konten“, so that medal and account never disagree.
 *
 * <p>The yearly achievement „Fortbildungsjahr“ (#1512) counts the regular training alone, in working
 * days, and none booked on a freelancer contract.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Authorized
public class TrainingAchievementService {

  /** An id no suborder has: an empty IN list is not portable SQL. */
  private static final Set<Long> NONE = Set.of(-1L);

  private final TrainingRepository trainingRepository;
  private final SpecialOrders specialOrders;
  private final AuthorizedUser authorizedUser;
  private final AuthorizedEmployee authorizedEmployee;

  /** The training of every person employed on the day, per day and ordered by day, by employee id - for the nightly job. */
  @Authorized(requiresManager = true)
  public Map<Long, List<TrainingDay>> getTrainingDaysOfEmployeesEmployedOn(LocalDate day) {
    return trainingRepository.findTrainingDaysOfEmployeesEmployedOn(regularTrainingSuborderIds(), day).stream()
        .collect(groupingBy(TrainingDay::employeeId));
  }

  /** All training of one person in minutes - the own, or anybody's for a manager. */
  public long getTrainingMinutes(long employeeId) {
    requireOwnOrManager(employeeId);
    return trainingRepository.sumTrainingMinutesOfEmployee(employeeId, regularTrainingSuborderIds());
  }

  /**
   * The regular training of every person employed on the day, up to the day, per contract and day and
   * ordered by day, by employee id - for the nightly job (#1512).
   */
  @Authorized(requiresManager = true)
  public Map<Long, List<RegularTrainingDay>> getRegularTrainingDaysOfEmployeesEmployedOn(LocalDate day) {
    return trainingRepository.findRegularTrainingDaysOfEmployeesEmployedOn(regularTrainingSuborderIds(), day).stream()
        .collect(groupingBy(RegularTrainingDay::employeeId));
  }

  /**
   * The working days of regular training of one person in the calendar year of {@code today}, up to
   * today, over all contracts of the year (#1512) - the own, or anybody's for a manager.
   */
  public WorkingDays getRegularTrainingWorkingDaysOfYear(long employeeId, LocalDate today) {
    requireOwnOrManager(employeeId);
    var sum = WorkingDays.ZERO;
    for (var day : trainingRepository.findRegularTrainingDaysOfEmployeeBetween(employeeId, regularTrainingSuborderIds(),
        today.withDayOfYear(1), today)) {
      sum = sum.plus(day.minutes(), day.dailyWorkingTime().toMinutes());
    }
    return sum;
  }

  private void requireOwnOrManager(long employeeId) {
    if (!authorizedUser.isManager() && !Objects.equals(authorizedEmployee.getEmployeeId(), employeeId)) {
      throw new AuthorizationException(AA_NOT_ATHORIZED);
    }
  }

  private Set<Long> regularTrainingSuborderIds() {
    var ids = specialOrders.getRegularTrainingSuborderIds();
    return ids.isEmpty() ? NONE : ids;
  }
}
