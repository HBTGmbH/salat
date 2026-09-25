package org.tb.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.NOT_WORKED;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.WORKED;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.service.AuthService;
import org.tb.common.exception.BusinessRuleException;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.WorkingdayRepository;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;

/**
 * Was geschieht, wenn das Einfügen am Unique Key auf Mitarbeitervertrag und Tag scheitert (#1111).
 *
 * <p>Der Zusammenstoß selbst ist in {@code WorkingdayConcurrentCreationTest} gegen eine echte
 * Datenbank festgehalten. Hier geht es um das, was danach passiert, und um den Fall, den eine
 * Datenbank nicht auf Ansage herstellt: dass sich die Ausgangslage <em>zwischen</em> den beiden
 * Versuchen ändert. Genau deshalb laufen die fachlichen Prüfungen im Wiederholungsfall erneut —
 * die Buchung, die der andere Vorgang angelegt hat, ist beim ersten Versuch noch nicht da.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class WorkingdayUpsertConflictTest {

  private static final long EMPLOYEE_CONTRACT_ID = 42L;
  private static final LocalDate DAY = LocalDate.of(2026, 3, 17);

  @InjectMocks
  private WorkingdayService workingdayService;

  @Mock
  private WorkingdayRepository workingdayRepository;
  @Mock
  private TimereportDAO timereportDAO;
  @Mock
  private AuthorizedUser authorizedUser;
  @Mock
  private AuthService authService;
  @Mock
  private PlatformTransactionManager transactionManager;

  private Employeecontract contract;

  @BeforeEach
  void authorizeAsManager() {
    when(authorizedUser.isManager()).thenReturn(true);

    var employee = new Employee();
    employee.setSign("w11");
    contract = new Employeecontract();
    setField(contract, "id", EMPLOYEE_CONTRACT_ID);
    contract.setEmployee(employee);
    contract.setValidFrom(LocalDate.of(2000, 1, 1));

    when(timereportDAO.getTimereportsByDateAndEmployeeContractId(anyLong(), any())).thenReturn(List.of());
  }

  @Test
  void the_change_is_applied_to_the_working_day_that_is_already_there() {
    var existing = storedWorkingday(8, 0);
    givenTheInsertViolatesTheUniqueKey();
    when(workingdayRepository.findByRefdayAndEmployeecontractId(DAY, EMPLOYEE_CONTRACT_ID))
        .thenReturn(Optional.of(existing));

    workingdayService.upsertWorkingday(newWorkingday(9, 30));

    var saved = ArgumentCaptor.forClass(Workingday.class);
    verify(workingdayRepository, atLeastOnce()).save(saved.capture());
    assertThat(saved.getValue()).isSameAs(existing);
    assertThat(existing.getStarttimehour()).isEqualTo(9);
    assertThat(existing.getStarttimeminute()).isEqualTo(30);
  }

  @Test
  void the_rules_are_checked_again_against_what_the_other_request_left_behind() {
    givenTheInsertViolatesTheUniqueKey();
    when(workingdayRepository.findByRefdayAndEmployeecontractId(DAY, EMPLOYEE_CONTRACT_ID))
        .thenReturn(Optional.of(storedWorkingday(8, 0)));
    // beim ersten Versuch gibt es noch keine Buchung, beim zweiten die des anderen Vorgangs
    when(timereportDAO.getTimereportsByDateAndEmployeeContractId(EMPLOYEE_CONTRACT_ID, DAY))
        .thenReturn(List.of(), List.of(TimereportDTO.builder().build()));

    var notWorked = newWorkingday(0, 0);
    notWorked.setType(NOT_WORKED);

    assertThatThrownBy(() -> workingdayService.upsertWorkingday(notWorked))
        .isInstanceOf(BusinessRuleException.class);
  }

  @Test
  void another_integrity_violation_stays_the_error_it_is() {
    givenTheInsertViolatesTheUniqueKey();
    when(workingdayRepository.findByRefdayAndEmployeecontractId(DAY, EMPLOYEE_CONTRACT_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> workingdayService.upsertWorkingday(newWorkingday(9, 30)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void an_already_stored_working_day_is_written_without_a_second_attempt() {
    var stored = storedWorkingday(8, 0);
    stored.setStarttimehour(11);

    workingdayService.upsertWorkingday(stored);

    verify(workingdayRepository).save(stored);
    verify(workingdayRepository, never()).findByRefdayAndEmployeecontractId(any(), anyLong());
  }

  private void givenTheInsertViolatesTheUniqueKey() {
    doThrow(new DataIntegrityViolationException("Duplicate entry for key 'workingday.workingday_uk1'"))
        .when(workingdayRepository).save(argThat(Workingday::isNew));
  }

  private Workingday newWorkingday(int startHour, int startMinute) {
    var workingday = new Workingday();
    workingday.setEmployeecontract(contract);
    workingday.setRefday(DAY);
    workingday.setStarttimehour(startHour);
    workingday.setStarttimeminute(startMinute);
    workingday.setType(WORKED);
    return workingday;
  }

  /** Ein Arbeitstag, den die Datenbank schon kennt: er trägt eine id und gilt damit nicht als neu. */
  private Workingday storedWorkingday(int startHour, int startMinute) {
    var workingday = newWorkingday(startHour, startMinute);
    setField(workingday, "id", 4711L);
    return workingday;
  }

}
