package de.hbt.salat.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import de.hbt.salat.dailyreport.auth.TimereportAuthorization;
import de.hbt.salat.dailyreport.domain.Workingday;
import de.hbt.salat.dailyreport.domain.Workingday.WorkingDayType;
import de.hbt.salat.dailyreport.persistence.TimereportDAO;
import de.hbt.salat.dailyreport.persistence.WorkingdayRepository;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeecontractService;

/**
 * Eine Buchung legt den Arbeitstag an, wo es noch keinen gibt, und ändert den Arbeitsbeginn eines
 * gearbeiteten Tages nie (#1274). Vorher wurden Stunde und Minute einzeln mit null verglichen: der
 * Beginn 07:00 bekam die Minute der nächsten Buchung ab 11:15 und stand danach auf 07:15.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class WorkingdaySeedTest {

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
  private EmployeecontractService employeecontractService;
  @Mock
  private PlatformTransactionManager transactionManager;
  @Mock
  private TimereportAuthorization timereportAuthorization;

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
    when(employeecontractService.getEmployeecontractById(EMPLOYEE_CONTRACT_ID)).thenReturn(contract);
    when(timereportDAO.getTimereportsByDateAndEmployeeContractId(anyLong(), any())).thenReturn(List.of());
    when(workingdayRepository.findByRefdayAndEmployeecontractId(any(), anyLong())).thenReturn(Optional.empty());
  }

  @Test
  void a_full_hour_start_keeps_its_minute_when_a_booking_begins_at_a_quarter_past() {
    var stored = storedWorkingday(WORKED, 7, 0);

    workingdayService.seedWorkingday(EMPLOYEE_CONTRACT_ID, DAY, 11, 15);

    assertStart(stored, 7, 0);
  }

  /** In der Dauer-Eingabe kommt der Beginn aus dem eingestellten Standard-Arbeitsbeginn. */
  @Test
  void a_full_hour_start_keeps_its_minute_against_the_preferred_start_of_the_day() {
    var stored = storedWorkingday(WORKED, 7, 0);

    workingdayService.seedWorkingday(EMPLOYEE_CONTRACT_ID, DAY, 8, 30);

    assertStart(stored, 7, 0);
  }

  /** 00:00 ist an einem gearbeiteten Tag ein bewusst gesetzter Beginn (#851). */
  @Test
  void a_start_at_midnight_on_a_worked_day_is_kept() {
    var stored = storedWorkingday(WORKED, 0, 0);

    workingdayService.seedWorkingday(EMPLOYEE_CONTRACT_ID, DAY, 9, 15);

    assertStart(stored, 0, 0);
  }

  @Test
  void a_start_within_the_hour_is_kept() {
    var stored = storedWorkingday(WORKED, 8, 45);

    workingdayService.seedWorkingday(EMPLOYEE_CONTRACT_ID, DAY, 10, 0);

    assertStart(stored, 8, 45);
  }

  /** Ein als „nicht gearbeitet" markierter Tag hat keinen Beginn; die Buchung bringt ihn mit. */
  @Test
  void a_day_marked_not_worked_gets_the_complete_begin_and_becomes_worked() {
    var stored = storedWorkingday(NOT_WORKED, 0, 0);

    workingdayService.seedWorkingday(EMPLOYEE_CONTRACT_ID, DAY, 9, 0);

    assertStart(stored, 9, 0);
    assertThat(stored.getType()).isEqualTo(WORKED);
  }

  @Test
  void a_day_without_working_day_gets_one_with_the_begin() {
    workingdayService.seedWorkingday(EMPLOYEE_CONTRACT_ID, DAY, 9, 15);

    var saved = ArgumentCaptor.forClass(Workingday.class);
    verify(workingdayRepository).save(saved.capture());
    assertThat(saved.getValue().getRefday()).isEqualTo(DAY);
    assertThat(saved.getValue().getType()).isEqualTo(WORKED);
    assertThat(saved.getValue().getStarttimehour()).isEqualTo(9);
    assertThat(saved.getValue().getStarttimeminute()).isEqualTo(15);
    assertThat(saved.getValue().getBreakhours()).isZero();
    assertThat(saved.getValue().getBreakminutes()).isZero();
  }

  private Workingday storedWorkingday(WorkingDayType type, int hour, int minute) {
    var stored = new Workingday();
    setField(stored, "id", 4711L);
    stored.setEmployeecontract(contract);
    stored.setRefday(DAY);
    stored.setType(type);
    stored.setStarttimehour(hour);
    stored.setStarttimeminute(minute);
    stored.setBreakhours(0);
    stored.setBreakminutes(type == WORKED ? 30 : 0);
    when(workingdayRepository.findByRefdayAndEmployeecontractId(DAY, EMPLOYEE_CONTRACT_ID)).thenReturn(Optional.of(stored));
    return stored;
  }

  private void assertStart(Workingday workingday, int hour, int minute) {
    verify(workingdayRepository).save(workingday);
    assertThat(workingday.getStarttimehour()).isEqualTo(hour);
    assertThat(workingday.getStarttimeminute()).isEqualTo(minute);
  }
}
