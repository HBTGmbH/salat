package de.hbt.salat.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static de.hbt.salat.dailyreport.domain.Workingday.WorkingDayType.NOT_WORKED;
import static de.hbt.salat.dailyreport.domain.Workingday.WorkingDayType.WORKED;

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
import org.springframework.transaction.PlatformTransactionManager;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.domain.Workingday;
import de.hbt.salat.dailyreport.persistence.TimereportDAO;
import de.hbt.salat.dailyreport.persistence.WorkingdayRepository;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;

/**
 * „Nicht gearbeitet" per Klick aus der Übersicht vor der Freigabe (#760): der Tag wird markiert wie
 * über den Schalter der Tagesansicht, mit denselben Prüfungen wie jedes Speichern eines Arbeitstags.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class WorkingdayMarkNotWorkedTest {

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
  void setUp() {
    var salatUser = new SalatUser();
    salatUser.setLoginname("w11");
    var employee = new Employee();
    employee.setSign("w11");
    employee.setSalatUser(salatUser);
    contract = new Employeecontract();
    setField(contract, "id", EMPLOYEE_CONTRACT_ID);
    contract.setEmployee(employee);
    contract.setValidFrom(LocalDate.of(2000, 1, 1));

    when(authorizedUser.getEffectiveLoginSign()).thenReturn("w11");
    when(timereportDAO.getTimereportsByDateAndEmployeeContractId(anyLong(), any())).thenReturn(List.of());
    when(workingdayRepository.findByRefdayAndEmployeecontractId(any(), anyLong())).thenReturn(Optional.empty());
  }

  @Test
  void a_day_without_working_day_gets_one_marked_not_worked() {
    workingdayService.markNotWorked(contract, DAY);

    var saved = ArgumentCaptor.forClass(Workingday.class);
    verify(workingdayRepository).save(saved.capture());
    assertThat(saved.getValue().getEmployeecontract()).isSameAs(contract);
    assertThat(saved.getValue().getRefday()).isEqualTo(DAY);
    assertThat(saved.getValue().getType()).isEqualTo(NOT_WORKED);
  }

  @Test
  void a_stored_working_day_becomes_not_worked_with_its_times_cleared() {
    var stored = new Workingday();
    setField(stored, "id", 4711L);
    stored.setEmployeecontract(contract);
    stored.setRefday(DAY);
    stored.setType(WORKED);
    stored.setStarttimehour(8);
    stored.setStarttimeminute(30);
    stored.setBreakhours(1);
    stored.setBreakminutes(15);
    when(workingdayRepository.findByRefdayAndEmployeecontractId(DAY, EMPLOYEE_CONTRACT_ID)).thenReturn(Optional.of(stored));

    workingdayService.markNotWorked(contract, DAY);

    verify(workingdayRepository).save(stored);
    assertThat(stored.getType()).isEqualTo(NOT_WORKED);
    assertThat(stored.getStarttimehour()).isZero();
    assertThat(stored.getStarttimeminute()).isZero();
    assertThat(stored.getBreakhours()).isZero();
    assertThat(stored.getBreakminutes()).isZero();
  }

  /** Steht inzwischen eine Buchung an dem Tag, etwa aus einem zweiten Fenster, wird nichts markiert. */
  @Test
  void a_day_with_a_booking_is_not_marked() {
    when(timereportDAO.getTimereportsByDateAndEmployeeContractId(EMPLOYEE_CONTRACT_ID, DAY))
        .thenReturn(List.of(TimereportDTO.builder().build()));

    assertThatThrownBy(() -> workingdayService.markNotWorked(contract, DAY))
        .isInstanceOf(BusinessRuleException.class);
    verify(workingdayRepository, never()).save(any());
  }

  /** Eine People Lead gibt frei, darf aber keine Arbeitstage anderer schreiben. */
  @Test
  void someone_who_may_not_write_the_working_day_is_refused() {
    when(authorizedUser.getEffectiveLoginSign()).thenReturn("epv");

    assertThatThrownBy(() -> workingdayService.markNotWorked(contract, DAY))
        .isInstanceOf(AuthorizationException.class);
    verify(workingdayRepository, never()).save(any());
  }

  @Test
  void a_day_outside_the_contract_is_not_marked() {
    contract.setValidFrom(DAY.plusDays(1));

    assertThatThrownBy(() -> workingdayService.markNotWorked(contract, DAY))
        .isInstanceOf(BusinessRuleException.class);
    verify(workingdayRepository, never()).save(any());
  }
}
