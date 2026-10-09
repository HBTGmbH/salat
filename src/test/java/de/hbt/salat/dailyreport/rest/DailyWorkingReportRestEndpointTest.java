package de.hbt.salat.dailyreport.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static de.hbt.salat.dailyreport.rest.DailyReportData.valueOf;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.domain.Workingday;
import de.hbt.salat.dailyreport.service.DailyWorkingReportService;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.dailyreport.service.WorkingdayService;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.EmployeecontractPeriod;
import de.hbt.salat.employee.service.EmployeecontractService;

@ExtendWith(MockitoExtension.class)
class DailyWorkingReportRestEndpointTest {

    private static final long EMPLOYEE_ID = 7L;
    private static final long OLD_CONTRACT_ID = 11L;
    private static final long NEW_CONTRACT_ID = 12L;

    @Mock
    EmployeecontractService employeecontractService;

    @Mock
    TimereportService timereportService;

    @Mock
    WorkingdayService workingdayService;

    @Mock
    DailyWorkingReportService dailyWorkingReportService;

    @Mock
    AuthorizedUser authorizedUser;

    @Mock
    AuthorizedEmployee authorizedEmployee;

    @InjectMocks
    DailyWorkingReportRestEndpoint dailyWorkingReportRestEndpoint;

    @Test
    void shouldGetReportsAcrossAContractChange() {
        // given
        var lastDayOfOldContract = DateUtils.parse("2022-03-31");
        var firstDayOfNewContract = DateUtils.parse("2022-04-01");
        var bookingOnOldContract = booking(lastDayOfOldContract, 0, 1);
        var bookingOnNewContract = booking(firstDayOfNewContract, 0, 2);
        var workingDayOnNewContract = workingday(firstDayOfNewContract, LocalTime.of(8, 30), Duration.ofMinutes(45));

        when(authorizedEmployee.getEmployeeId()).thenReturn(EMPLOYEE_ID);
        when(authorizedUser.isAuthenticated()).thenReturn(true);
        when(employeecontractService.getEmployeecontractPeriodsBetween(EMPLOYEE_ID, lastDayOfOldContract, firstDayOfNewContract))
                .thenReturn(List.of(
                        new EmployeecontractPeriod(OLD_CONTRACT_ID, lastDayOfOldContract, lastDayOfOldContract),
                        new EmployeecontractPeriod(NEW_CONTRACT_ID, firstDayOfNewContract, firstDayOfNewContract)));
        when(workingdayService.getWorkingdaysByEmployeeContractId(OLD_CONTRACT_ID, lastDayOfOldContract, lastDayOfOldContract))
                .thenReturn(List.of());
        when(workingdayService.getWorkingdaysByEmployeeContractId(NEW_CONTRACT_ID, firstDayOfNewContract, firstDayOfNewContract))
                .thenReturn(List.of(workingDayOnNewContract));
        when(timereportService.getTimereportsByDatesAndEmployeeContractId(OLD_CONTRACT_ID, lastDayOfOldContract, lastDayOfOldContract))
                .thenReturn(List.of(bookingOnOldContract));
        when(timereportService.getTimereportsByDatesAndEmployeeContractId(NEW_CONTRACT_ID, firstDayOfNewContract, firstDayOfNewContract))
                .thenReturn(List.of(bookingOnNewContract));

        // when
        var result = dailyWorkingReportRestEndpoint.getReports(lastDayOfOldContract, 2, false);

        // then
        assertThat(result.getBody())
                .extracting(DailyWorkingReportData::getDate, DailyWorkingReportData::getStartTime,
                        DailyWorkingReportData::getBreakDuration, DailyWorkingReportData::getDailyReports)
                .containsExactly(
                        tuple(lastDayOfOldContract, null, null, List.of(valueOf(bookingOnOldContract))),
                        tuple(firstDayOfNewContract, LocalTime.of(8, 30), LocalTime.of(0, 45), List.of(valueOf(bookingOnNewContract))));
    }

    @Test
    void shouldGetReportsFromContractStartWhenThePeriodStartsBeforeIt() {
        // given
        var dayBeforeContract = DateUtils.parse("2019-03-31");
        var firstDayOfContract = DateUtils.parse("2019-04-01");

        when(authorizedEmployee.getEmployeeId()).thenReturn(EMPLOYEE_ID);
        when(authorizedUser.isAuthenticated()).thenReturn(true);
        when(employeecontractService.getEmployeecontractPeriodsBetween(EMPLOYEE_ID, dayBeforeContract, firstDayOfContract))
                .thenReturn(List.of(new EmployeecontractPeriod(NEW_CONTRACT_ID, firstDayOfContract, firstDayOfContract)));
        when(workingdayService.getWorkingdaysByEmployeeContractId(NEW_CONTRACT_ID, firstDayOfContract, firstDayOfContract))
                .thenReturn(List.of());
        when(timereportService.getTimereportsByDatesAndEmployeeContractId(NEW_CONTRACT_ID, firstDayOfContract, firstDayOfContract))
                .thenReturn(List.of(booking(firstDayOfContract, 0, 1)));

        // when
        var result = dailyWorkingReportRestEndpoint.getReports(dayBeforeContract, 2, false);

        // then
        assertThat(result.getBody())
                .extracting(DailyWorkingReportData::getDate)
                .containsExactly(firstDayOfContract);
    }

    /** The query of a period sorts by order; each day keeps the order of the day view, by sequence. */
    @Test
    void shouldGetBookingsOfADayBySequenceAndLeaveOutDaysWithoutAny() {
        // given
        var day = DateUtils.parse("2024-07-08");
        var second = booking(day, 1, 1);
        var first = booking(day, 0, 2);
        var lastDay = day.plusDays(2);

        when(authorizedEmployee.getEmployeeId()).thenReturn(EMPLOYEE_ID);
        when(authorizedUser.isAuthenticated()).thenReturn(true);
        when(employeecontractService.getEmployeecontractPeriodsBetween(EMPLOYEE_ID, day, lastDay))
                .thenReturn(List.of(new EmployeecontractPeriod(NEW_CONTRACT_ID, day, lastDay)));
        when(workingdayService.getWorkingdaysByEmployeeContractId(NEW_CONTRACT_ID, day, lastDay))
                .thenReturn(List.of());
        when(timereportService.getTimereportsByDatesAndEmployeeContractId(NEW_CONTRACT_ID, day, lastDay))
                .thenReturn(List.of(second, first));

        // when
        var result = dailyWorkingReportRestEndpoint.getReports(day, 3, false);

        // then
        assertThat(result.getBody())
                .extracting(DailyWorkingReportData::getDate, DailyWorkingReportData::getDailyReports)
                .containsExactly(tuple(day, List.of(valueOf(first), valueOf(second))));
    }

    @Test
    void shouldAnswerNotFoundWithAReasonWhenNoContractIsValidInThePeriod() {
        // given
        var day = DateUtils.parse("2016-10-08");

        when(authorizedEmployee.getEmployeeId()).thenReturn(EMPLOYEE_ID);
        when(authorizedUser.isAuthenticated()).thenReturn(true);
        when(employeecontractService.getEmployeecontractPeriodsBetween(EMPLOYEE_ID, day, day.plusDays(1)))
                .thenReturn(List.of());

        // when
        assertThatThrownBy(() -> dailyWorkingReportRestEndpoint.getReports(day, 2, false))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(NOT_FOUND);
                    assertThat(e.getReason()).contains("2016-10-08", "2016-10-09");
                });
        verifyNoInteractions(timereportService, workingdayService);
    }

    private TimereportDTO booking(LocalDate day, int sequencenumber, int hours) {
        return TimereportDTO.builder()
                .referenceday(day).sequencenumber(sequencenumber).duration(Duration.ofHours(hours))
                .build();
    }

    private Workingday workingday(LocalDate day, LocalTime start, Duration breakLength) {
        var workingday = new Workingday();
        workingday.setRefday(day);
        workingday.setStartTime(start);
        workingday.setBreakLength(breakLength);
        return workingday;
    }

}
