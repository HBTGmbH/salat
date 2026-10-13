package de.hbt.salat.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static de.hbt.salat.common.GlobalConstants.TIMEREPORT_STATUS_OPEN;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
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
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.dailyreport.auth.TimereportAuthorization;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.domain.SurchargeKind;

/**
 * The time surcharge counts against the target in the monthly list as on the time account (#1469): the difference of
 * the month and the balance of yesterday are booked plus surcharge minus target. The booked total stays the booked
 * time.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class DailyServiceSurchargeTest {

  private static final long CONTRACT_ID = 42L;
  /** A month long past, so that there is no balance of yesterday in it. */
  private static final YearMonth MONTH = YearMonth.of(2026, 5);

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
  private AuthorizedUser authorizedUser;
  @Mock
  private AuthService authService;

  private DailyService dailyService;

  @BeforeEach
  void setUp() {
    dailyService = new DailyService(timereportService, workingdayService, publicholidayService, overtimeService,
        employeecontractService, new TimereportAuthorization(authorizedUser, authService, employeecontractService));
    var contract = new Employeecontract();
    setField(contract, "id", CONTRACT_ID);
    var salatUser = new SalatUser();
    salatUser.setLoginname("own");
    var employee = new Employee();
    employee.setSign("own");
    employee.setSalatUser(salatUser);
    contract.setEmployee(employee);
    contract.setSupervisors(new ArrayList<>());
    contract.setValidFrom(LocalDate.of(2000, 1, 1));
    contract.setDailyWorkingTime(Duration.ofHours(8));
    when(employeecontractService.getEmployeecontractById(CONTRACT_ID)).thenReturn(contract);
    when(overtimeService.calculateWorkingTimeTarget(eq(CONTRACT_ID), any(), any())).thenReturn(Duration.ofHours(160));
    when(workingdayService.getWorkingdaysByEmployeeContractId(anyLong(), any(), any())).thenReturn(List.of());
    when(publicholidayService.getPublicHolidaysBetween(any(), any())).thenReturn(List.of());
  }

  @Test
  void the_difference_of_the_month_counts_the_surcharge_against_the_target() {
    when(timereportService.getTimereportsByDatesAndEmployeeContractId(CONTRACT_ID, MONTH.atDay(1), MONTH.atEndOfMonth()))
        .thenReturn(List.of(TimereportDTO.builder().id(1L).employeecontractId(CONTRACT_ID).referenceday(MONTH.atDay(9))
            .status(TIMEREPORT_STATUS_OPEN).duration(Duration.ofHours(3))
            .surchargeKind(SurchargeKind.SATURDAY).surchargePercent(50).surchargeCredit(Duration.ofMinutes(90)).build()));

    var list = dailyService.buildListView(MONTH, CONTRACT_ID);

    assertThat(list.monthTotal()).isEqualTo("3:00");
    assertThat(list.monthDiff()).isEqualTo("-155:30");
  }
}
