package org.tb.dailyreport.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.NOT_WORKED;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.WORKED;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.dailyreport.domain.Workingday.WorkingDayType;
import org.tb.employee.domain.Employeecontract;

/**
 * The rule "working day without booking" (#1124) that release and the dashboard hint share. The
 * week of 2024-03-04 (Monday) to 2024-03-10 (Sunday) serves as the period; holidays, working days
 * and bookings are passed in, so every case names the one input that decides it.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class UnbookedWorkingDaysTest {

  private static final LocalDate MONDAY = LocalDate.of(2024, 3, 4);
  private static final LocalDate TUESDAY = MONDAY.plusDays(1);
  private static final LocalDate WEDNESDAY = MONDAY.plusDays(2);
  private static final LocalDate THURSDAY = MONDAY.plusDays(3);
  private static final LocalDate FRIDAY = MONDAY.plusDays(4);
  private static final LocalDate SATURDAY = MONDAY.plusDays(5);
  private static final LocalDate SUNDAY = MONDAY.plusDays(6);

  private static final Employeecontract OPEN_ENDED = contract(MONDAY.minusYears(1), null);

  @Test
  void every_weekday_without_booking_is_reported_in_ascending_order() {
    assertThat(UnbookedWorkingDays.between(MONDAY, SUNDAY, OPEN_ENDED, Set.of(), Map.of(), Set.of()))
        .containsExactly(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY);
  }

  @Test
  void the_weekend_is_never_a_working_day() {
    assertThat(UnbookedWorkingDays.between(SATURDAY, SUNDAY, OPEN_ENDED, Set.of(), Map.of(), Set.of()))
        .isEmpty();
  }

  /** A working day recorded on a Saturday does not make the Saturday a working day of the rule. */
  @Test
  void a_worked_saturday_without_booking_is_not_reported() {
    assertThat(UnbookedWorkingDays.between(SATURDAY, SUNDAY, OPEN_ENDED, Set.of(),
        workingDays(workingday(SATURDAY, WORKED)), Set.of()))
        .isEmpty();
  }

  @Test
  void a_public_holiday_on_a_weekday_is_not_reported() {
    assertThat(UnbookedWorkingDays.between(MONDAY, FRIDAY, OPEN_ENDED, Set.of(), Map.of(), Set.of(WEDNESDAY)))
        .containsExactly(MONDAY, TUESDAY, THURSDAY, FRIDAY);
  }

  @Test
  void a_day_marked_not_worked_is_not_reported() {
    assertThat(UnbookedWorkingDays.between(MONDAY, FRIDAY, OPEN_ENDED, Set.of(),
        workingDays(workingday(FRIDAY, NOT_WORKED)), Set.of()))
        .containsExactly(MONDAY, TUESDAY, WEDNESDAY, THURSDAY);
  }

  /** A start of work and a break alone are no booking: the day still has a gap. */
  @Test
  void a_worked_day_without_booking_is_reported() {
    assertThat(UnbookedWorkingDays.between(THURSDAY, FRIDAY, OPEN_ENDED, Set.of(),
        workingDays(workingday(THURSDAY, WORKED)), Set.of()))
        .containsExactly(THURSDAY, FRIDAY);
  }

  @Test
  void a_booked_day_is_not_reported() {
    assertThat(UnbookedWorkingDays.between(MONDAY, FRIDAY, OPEN_ENDED, Set.of(TUESDAY, THURSDAY), Map.of(), Set.of()))
        .containsExactly(MONDAY, WEDNESDAY, FRIDAY);
  }

  @Test
  void days_before_the_contract_begins_are_not_reported() {
    var beginsWednesday = contract(WEDNESDAY, null);

    assertThat(UnbookedWorkingDays.between(MONDAY, FRIDAY, beginsWednesday, Set.of(), Map.of(), Set.of()))
        .containsExactly(WEDNESDAY, THURSDAY, FRIDAY);
  }

  @Test
  void days_after_the_contract_ends_are_not_reported() {
    var endsWednesday = contract(MONDAY.minusYears(1), WEDNESDAY);

    assertThat(UnbookedWorkingDays.between(MONDAY, FRIDAY, endsWednesday, Set.of(), Map.of(), Set.of()))
        .containsExactly(MONDAY, TUESDAY, WEDNESDAY);
  }

  /** The dashboard passes a fixed Monday-to-Sunday window; the rule clips it to the contract. */
  @Test
  void a_contract_inside_the_period_clips_it_on_both_sides() {
    var tuesdayToThursday = contract(TUESDAY, THURSDAY);

    assertThat(UnbookedWorkingDays.between(MONDAY, SUNDAY, tuesdayToThursday, Set.of(), Map.of(), Set.of()))
        .containsExactly(TUESDAY, WEDNESDAY, THURSDAY);
  }

  @Test
  void a_contract_outside_the_period_leaves_nothing_to_report() {
    var endedBefore = contract(MONDAY.minusYears(1), MONDAY.minusDays(1));

    assertThat(UnbookedWorkingDays.between(MONDAY, SUNDAY, endedBefore, Set.of(), Map.of(), Set.of()))
        .isEmpty();
  }

  /** Releasing up to the date of the last release: the period starts the day after it ends. */
  @Test
  void a_period_ending_the_day_before_it_begins_is_empty() {
    assertThat(UnbookedWorkingDays.between(WEDNESDAY, TUESDAY, OPEN_ENDED, Set.of(), Map.of(), Set.of()))
        .isEmpty();
  }

  /**
   * Kept from the loop the rule was extracted from ({@code LocalDate.datesUntil}); a caller that can
   * produce such a period has to check it first.
   */
  @Test
  void a_period_ending_further_back_is_rejected() {
    assertThatThrownBy(() -> UnbookedWorkingDays.between(WEDNESDAY, MONDAY, OPEN_ENDED, Set.of(), Map.of(), Set.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static Employeecontract contract(LocalDate validFrom, LocalDate validUntil) {
    var contract = new Employeecontract();
    contract.setValidFrom(validFrom);
    contract.setValidUntil(validUntil);
    return contract;
  }

  private static Workingday workingday(LocalDate date, WorkingDayType type) {
    var workingday = new Workingday();
    workingday.setRefday(date);
    workingday.setType(type);
    return workingday;
  }

  private static Map<LocalDate, Workingday> workingDays(Workingday... workingdays) {
    return Arrays.stream(workingdays).collect(Collectors.toMap(Workingday::getRefday, Function.identity()));
  }
}
