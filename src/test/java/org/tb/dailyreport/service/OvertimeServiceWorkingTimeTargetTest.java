package org.tb.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.persistence.PublicholidayDAO;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.service.EmployeeorderService;

/**
 * The working time target of a <em>single</em> day. The daily view derives its target from this
 * calculation instead of reading the contract's daily working time directly, so that weekends,
 * public holidays and days outside the contract carry no target (#857).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OvertimeServiceWorkingTimeTargetTest {

  private static final long EMPLOYEE_CONTRACT_ID = 1L;
  private static final Duration DAILY_WORKING_TIME = Duration.ofHours(8);
  private static final LocalDate CONTRACT_VALID_FROM = LocalDate.parse("2020-01-01");

  private static final LocalDate MONDAY = LocalDate.parse("2026-07-13");
  private static final LocalDate SATURDAY = LocalDate.parse("2026-07-18");
  private static final LocalDate SUNDAY = LocalDate.parse("2026-07-19");

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

  @Test
  void a_normal_weekday_carries_the_contract_daily_working_time() {
    givenContract(null);
    givenNoHolidays();

    assertThat(targetOf(MONDAY)).isEqualTo(DAILY_WORKING_TIME);
  }

  @Test
  void a_saturday_carries_no_target() {
    givenContract(null);
    givenNoHolidays();

    assertThat(targetOf(SATURDAY)).isZero();
  }

  @Test
  void a_sunday_carries_no_target() {
    givenContract(null);
    givenNoHolidays();

    assertThat(targetOf(SUNDAY)).isZero();
  }

  @Test
  void a_public_holiday_on_a_weekday_carries_no_target() {
    givenContract(null);
    givenHoliday(MONDAY, "Testfeiertag");

    assertThat(targetOf(MONDAY)).isZero();
  }

  @Test
  void a_public_holiday_on_a_weekend_is_not_subtracted_twice() {
    givenContract(null);
    givenHoliday(SATURDAY, "Testfeiertag");

    // the weekend already brings the target to zero; subtracting the holiday on top would make it
    // negative and hand out an hour of overtime for doing nothing
    assertThat(targetOf(SATURDAY)).isZero();
  }

  @Test
  void a_weekday_before_the_contract_starts_carries_no_target() {
    givenContract(null);

    assertThat(targetOf(CONTRACT_VALID_FROM.minusDays(7))).isZero();
  }

  @Test
  void a_weekday_after_the_contract_ended_carries_no_target() {
    givenContract(MONDAY.minusDays(7));

    assertThat(targetOf(MONDAY)).isZero();
  }

  private Duration targetOf(LocalDate day) {
    return overtimeService.calculateWorkingTimeTarget(EMPLOYEE_CONTRACT_ID, day, day);
  }

  private void givenContract(LocalDate validUntil) {
    Employeecontract contract = new Employeecontract();
    contract.setValidFrom(CONTRACT_VALID_FROM);
    contract.setValidUntil(validUntil);
    contract.setDailyWorkingTime(DAILY_WORKING_TIME);
    when(employeecontractDAO.getEmployeecontractById(EMPLOYEE_CONTRACT_ID)).thenReturn(contract);
  }

  private void givenNoHolidays() {
    when(publicholidayDAO.getPublicHolidaysBetween(any(), any())).thenReturn(List.of());
  }

  private void givenHoliday(LocalDate date, String name) {
    when(publicholidayDAO.getPublicHolidaysBetween(any(), any()))
        .thenReturn(List.of(new Publicholiday(date, name)));
  }

}
