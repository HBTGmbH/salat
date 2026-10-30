package de.hbt.salat.employee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.common.util.ClockProvider;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.domain.OverdueRelease;
import de.hbt.salat.employee.persistence.EmployeecontractRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link EmployeecontractService#getRunningContractsReleasedBefore} answers the automatic reminder
 * (#215, #1557) which running contracts lag behind with their release, as values.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class OverdueReleaseTest {

  /** Thursday, 1 October 2026: due is the release until 30 September. */
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
  private static final LocalDate END_OF_SEPTEMBER = LocalDate.of(2026, 9, 30);

  @Mock
  private EmployeecontractRepository employeecontractRepository;

  @InjectMocks
  private EmployeecontractService service;

  @BeforeEach
  void pinTheClock() {
    ClockProvider.setClock(Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
  }

  @AfterEach
  void resetTheClock() {
    ClockProvider.reset();
  }

  @Test
  void a_running_contract_released_until_august_is_overdue_with_its_person_and_people_leads() {
    var contract = contract(1L, LocalDate.of(2026, 8, 31), null, person(42L), person(43L), person(null));
    when(employeecontractRepository.findAllReleasedWithPeople()).thenReturn(List.of(contract));

    assertThat(service.getRunningContractsReleasedBefore(END_OF_SEPTEMBER)).containsExactly(
        new OverdueRelease(1L, 42L, LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 30), List.of(43L)));
  }

  @Test
  void a_contract_released_until_the_end_of_the_previous_month_is_not_overdue() {
    var contract = contract(1L, LocalDate.of(2026, 9, 30), null, person(42L));
    when(employeecontractRepository.findAllReleasedWithPeople()).thenReturn(List.of(contract));

    assertThat(service.getRunningContractsReleasedBefore(END_OF_SEPTEMBER)).isEmpty();
  }

  /** Ended before today: the person may have left, whoever still has to release it is reminded by hand. */
  @Test
  void a_contract_that_ended_before_today_is_left_out_although_its_release_lags_behind() {
    var contract = contract(1L, LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 30), person(42L));
    when(employeecontractRepository.findAllReleasedWithPeople()).thenReturn(List.of(contract));

    assertThat(service.getRunningContractsReleasedBefore(END_OF_SEPTEMBER)).isEmpty();
  }

  @Test
  void a_contract_ending_today_still_runs() {
    var contract = contract(1L, LocalDate.of(2026, 8, 31), TODAY, person(42L));
    when(employeecontractRepository.findAllReleasedWithPeople()).thenReturn(List.of(contract));

    assertThat(service.getRunningContractsReleasedBefore(END_OF_SEPTEMBER)).extracting(OverdueRelease::employeecontractId)
        .containsExactly(1L);
  }

  @Test
  void a_person_without_a_login_is_kept_with_none() {
    var contract = contract(1L, LocalDate.of(2026, 8, 31), null, person(null));
    when(employeecontractRepository.findAllReleasedWithPeople()).thenReturn(List.of(contract));

    assertThat(service.getRunningContractsReleasedBefore(END_OF_SEPTEMBER)).extracting(OverdueRelease::salatUserId)
        .containsExactly((Long) null);
  }

  /** On its last day a contract ending before the end of the month is due until that day, not further. */
  @Test
  void a_contract_ending_before_the_day_asked_for_is_due_until_its_last_day() {
    var lastDay = LocalDate.of(2026, 10, 30);
    ClockProvider.setClock(Clock.fixed(lastDay.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
    var overdue = contract(1L, LocalDate.of(2026, 9, 30), lastDay, person(42L));
    var releasedToItsEnd = contract(2L, lastDay, lastDay, person(42L));
    when(employeecontractRepository.findAllReleasedWithPeople()).thenReturn(List.of(overdue, releasedToItsEnd));

    assertThat(service.getRunningContractsReleasedBefore(LocalDate.of(2026, 10, 31))).containsExactly(
        new OverdueRelease(1L, 42L, LocalDate.of(2026, 9, 30), lastDay, List.of()));
  }

  private static Employeecontract contract(long id, LocalDate releasedUntil, LocalDate validUntil, Employee person,
                                           Employee... peopleLeads) {
    var contract = new Employeecontract();
    ReflectionTestUtils.setField(contract, "id", id);
    contract.setEmployee(person);
    contract.setSupervisors(new ArrayList<>(List.of(peopleLeads)));
    contract.setValidFrom(LocalDate.of(2020, 1, 1));
    contract.setValidUntil(validUntil);
    contract.setReportReleaseDate(releasedUntil);
    return contract;
  }

  private static Employee person(Long salatUserId) {
    var employee = new Employee();
    if (salatUserId != null) {
      var login = new SalatUser();
      ReflectionTestUtils.setField(login, "id", salatUserId);
      employee.setSalatUser(login);
    }
    return employee;
  }
}
