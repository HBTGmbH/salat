package de.hbt.salat.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.dailyreport.domain.MonthlyReportedMinutes;
import de.hbt.salat.dailyreport.domain.ReportedMinutes;
import de.hbt.salat.dailyreport.persistence.PublicholidayDAO;
import de.hbt.salat.dailyreport.persistence.TimereportDAO;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.persistence.EmployeecontractDAO;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.service.EmployeeorderService;

/**
 * The credit for night, weekend and holiday assignments on the overtime account (#1469): it raises the
 * balance like booked time and is reported apart from it — in the balance of a period, in the monthly
 * report and as part of the total.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class OvertimeServiceSurchargeTest {

  private static final long EMPLOYEE_CONTRACT_ID = 1L;
  private static final Duration DAILY_WORKING_TIME = Duration.ofHours(8);
  // a past year, so that the monthly report covers both months whatever day the test runs
  private static final int YEAR = Year.now().getValue() - 1;
  private static final LocalDate JULY = LocalDate.of(YEAR, 7, 1);
  private static final LocalDate END_OF_JULY = LocalDate.of(YEAR, 7, 31);
  private static final LocalDate AUGUST = LocalDate.of(YEAR, 8, 1);
  private static final LocalDate END_OF_AUGUST = LocalDate.of(YEAR, 8, 31);

  @InjectMocks
  private OvertimeService overtimeService;

  @Mock
  private EmployeecontractDAO employeecontractDAO;
  @Mock
  private PublicholidayDAO publicholidayDAO;
  @Mock
  private TimereportDAO timereportDAO;
  @Mock
  private EmployeecontractService employeecontractService;
  @Mock
  private TimereportService timereportService;
  @Mock
  private EmployeeorderService employeeorderService;

  @BeforeEach
  void setUp() {
    var contract = new Employeecontract();
    ReflectionTestUtils.setField(contract, "id", EMPLOYEE_CONTRACT_ID);
    contract.setValidFrom(JULY);
    contract.setValidUntil(END_OF_AUGUST);
    contract.setDailyWorkingTime(DAILY_WORKING_TIME);
    when(employeecontractDAO.getEmployeecontractById(EMPLOYEE_CONTRACT_ID)).thenReturn(contract);
    when(publicholidayDAO.getPublicHolidaysBetween(any(), any())).thenReturn(List.of());
    when(employeecontractService.getOvertimeAdjustmentsByEmployeeContractId(anyLong())).thenReturn(List.of());
    when(timereportDAO.getReportedMinutesForEmployeeContract(anyLong(), any(), any())).thenReturn(new ReportedMinutes(null, null));
  }

  @Test
  void the_credit_raises_the_balance_and_stands_apart_from_the_booked_time() {
    var target = overtimeService.calculateWorkingTimeTarget(EMPLOYEE_CONTRACT_ID, AUGUST, END_OF_AUGUST);
    var booked = target.plusHours(2);
    when(timereportDAO.getReportedMinutesForEmployeeContract(EMPLOYEE_CONTRACT_ID, AUGUST, END_OF_AUGUST))
        .thenReturn(new ReportedMinutes(booked.toMinutes(), 150L));

    var balance = overtimeService.calculateOvertimeBalance(EMPLOYEE_CONTRACT_ID, AUGUST, END_OF_AUGUST).orElseThrow();

    assertThat(balance.workingTime()).isEqualTo(booked);
    assertThat(balance.surchargeCredit()).isEqualTo(Duration.ofMinutes(150));
    // 170:00 booked - 168:00 target + 2:30 credit, in whatever month
    assertThat(balance.diff()).isEqualTo(Duration.ofMinutes(4 * 60 + 30));
  }

  @Test
  void without_a_credit_the_balance_is_what_it_was() {
    var target = overtimeService.calculateWorkingTimeTarget(EMPLOYEE_CONTRACT_ID, AUGUST, END_OF_AUGUST);
    when(timereportDAO.getReportedMinutesForEmployeeContract(EMPLOYEE_CONTRACT_ID, AUGUST, END_OF_AUGUST))
        .thenReturn(new ReportedMinutes(target.toMinutes(), 0L));

    var balance = overtimeService.calculateOvertimeBalance(EMPLOYEE_CONTRACT_ID, AUGUST, END_OF_AUGUST).orElseThrow();

    assertThat(balance.surchargeCredit()).isZero();
    assertThat(balance.diff()).isZero();
  }

  @Test
  void the_monthly_report_shows_the_credit_in_a_column_of_its_own_and_sums_it() {
    var julyTarget = overtimeService.calculateWorkingTimeTarget(EMPLOYEE_CONTRACT_ID, JULY, JULY.withDayOfMonth(31));
    var augustTarget = overtimeService.calculateWorkingTimeTarget(EMPLOYEE_CONTRACT_ID, AUGUST, END_OF_AUGUST);
    when(timereportDAO.getReportedMinutesByMonthForEmployeeContract(eq(EMPLOYEE_CONTRACT_ID), any(), any()))
        .thenReturn(List.of(
            new MonthlyReportedMinutes(YEAR, 7, julyTarget.toMinutes(), 240L),
            new MonthlyReportedMinutes(YEAR, 8, augustTarget.plusHours(2).toMinutes(), 150L)));

    var report = overtimeService.createDetailedReportForEmployee(EMPLOYEE_CONTRACT_ID, false);

    var august = report.getMonths().getFirst();
    assertThat(august.getSurchargeCredit()).isEqualTo(Duration.ofMinutes(150));
    assertThat(august.getSum()).isEqualTo(augustTarget.plusHours(2).plusMinutes(150));
    assertThat(august.getDiff()).isEqualTo(Duration.ofMinutes(4 * 60 + 30));
    assertThat(august.getDiffCumulative()).isEqualTo(Duration.ofMinutes(8 * 60 + 30));
    assertThat(report.getTotal().getSurchargeCredit()).isEqualTo(Duration.ofMinutes(390));
    assertThat(report.getTotal().getDiff()).isEqualTo(Duration.ofMinutes(8 * 60 + 30));
  }

  /*
   * The total names its share of the credit from the same calculation as itself (#1469): the part up to the
   * acceptance is frozen in the static balance and read apart, the rest comes with the dynamic part.
   */
  @Test
  void the_share_of_the_credit_in_the_total_covers_the_accepted_and_the_open_part() {
    var contract = employeecontractDAO.getEmployeecontractById(EMPLOYEE_CONTRACT_ID);
    contract.setReportAcceptanceDate(END_OF_JULY);
    contract.setOvertimeStatic(Duration.ofHours(4));
    when(timereportDAO.getReportedMinutesForEmployeeContract(EMPLOYEE_CONTRACT_ID, JULY, END_OF_JULY))
        .thenReturn(new ReportedMinutes(0L, 240L));
    when(timereportDAO.getReportedMinutesForEmployeeContract(eq(EMPLOYEE_CONTRACT_ID), eq(AUGUST), any()))
        .thenReturn(new ReportedMinutes(0L, 150L));

    var total = overtimeService.calculateOvertime(EMPLOYEE_CONTRACT_ID, false).orElseThrow().getTotal();

    assertThat(total.getSurchargeCredit()).isEqualTo(Duration.ofMinutes(390));
    verify(timereportDAO).getReportedMinutesForEmployeeContract(EMPLOYEE_CONTRACT_ID, JULY, END_OF_JULY);
  }

  @Test
  void a_balance_without_any_credit_has_no_share() {
    var total = overtimeService.calculateOvertime(EMPLOYEE_CONTRACT_ID, false).orElseThrow().getTotal();

    assertThat(total.getSurchargeCredit()).isZero();
  }
}
