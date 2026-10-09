package de.hbt.salat.dailyreport.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.service.DailyWorkingReportService;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.dailyreport.service.WorkingdayService;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeecontractService;

@ExtendWith(MockitoExtension.class)
class DailyWorkingReportRestEndpointTest {

    private static final AtomicLong IDS = new AtomicLong();

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
        var bookingOnOldContract = TimereportDTO.builder().duration(Duration.ofHours(1)).build();
        var bookingOnNewContract = TimereportDTO.builder().duration(Duration.ofHours(2)).build();
        var employee = employee();
        var oldContract = employeeContract(employee);
        var newContract = employeeContract(employee);

        when(authorizedEmployee.getEmployeeId()).thenReturn(employee.getId());
        when(authorizedUser.isAuthenticated()).thenReturn(true);
        when(employeecontractService.getEmployeeContractValidAt(employee.getId(), lastDayOfOldContract))
                .thenReturn(oldContract);
        when(employeecontractService.getEmployeeContractValidAt(employee.getId(), firstDayOfNewContract))
                .thenReturn(newContract);
        when(timereportService.getTimereportsByDateAndEmployeeContractId(oldContract.getId(), lastDayOfOldContract))
                .thenReturn(List.of(bookingOnOldContract));
        when(timereportService.getTimereportsByDateAndEmployeeContractId(newContract.getId(), firstDayOfNewContract))
                .thenReturn(List.of(bookingOnNewContract));

        // when
        var result = dailyWorkingReportRestEndpoint.getReports(lastDayOfOldContract, 2, false);

        // then
        assertThat(result.getBody())
                .extracting(DailyWorkingReportData::getDate, DailyWorkingReportData::getDailyReports)
                .containsExactly(
                        tuple(lastDayOfOldContract, List.of(DailyReportData.valueOf(bookingOnOldContract))),
                        tuple(firstDayOfNewContract, List.of(DailyReportData.valueOf(bookingOnNewContract))));
        verify(workingdayService).getWorkingday(oldContract.getId(), lastDayOfOldContract);
        verify(workingdayService).getWorkingday(newContract.getId(), firstDayOfNewContract);
    }

    @Test
    void shouldGetReportsFromContractStartWhenThePeriodStartsBeforeIt() {
        // given
        var dayBeforeContract = DateUtils.parse("2019-03-31");
        var firstDayOfContract = DateUtils.parse("2019-04-01");
        var booking = TimereportDTO.builder().duration(Duration.ofHours(1)).build();
        var employee = employee();
        var employeeContract = employeeContract(employee);

        when(authorizedEmployee.getEmployeeId()).thenReturn(employee.getId());
        when(authorizedUser.isAuthenticated()).thenReturn(true);
        when(employeecontractService.getEmployeeContractValidAt(employee.getId(), dayBeforeContract))
                .thenReturn(null);
        when(employeecontractService.getEmployeeContractValidAt(employee.getId(), firstDayOfContract))
                .thenReturn(employeeContract);
        when(timereportService.getTimereportsByDateAndEmployeeContractId(employeeContract.getId(), firstDayOfContract))
                .thenReturn(List.of(booking));

        // when
        var result = dailyWorkingReportRestEndpoint.getReports(dayBeforeContract, 2, false);

        // then
        assertThat(result.getBody())
                .extracting(DailyWorkingReportData::getDate)
                .containsExactly(firstDayOfContract);
    }

    @Test
    void shouldAnswerNotFoundWithAReasonWhenNoContractIsValidInThePeriod() {
        // given
        var day = DateUtils.parse("2016-10-08");
        var employee = employee();

        when(authorizedEmployee.getEmployeeId()).thenReturn(employee.getId());
        when(authorizedUser.isAuthenticated()).thenReturn(true);
        when(employeecontractService.getEmployeeContractValidAt(eq(employee.getId()), any(LocalDate.class)))
                .thenReturn(null);

        // when
        assertThatThrownBy(() -> dailyWorkingReportRestEndpoint.getReports(day, 2, false))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(NOT_FOUND);
                    assertThat(e.getReason()).contains("2016-10-08", "2016-10-09");
                });
        verifyNoInteractions(timereportService, workingdayService);
    }

    private Employee employee() {
        Employee res = new Employee();
        ReflectionTestUtils.setField(res, "id", IDS.incrementAndGet());
        return res;
    }

    private Employeecontract employeeContract(Employee employee) {
        Employeecontract res = new Employeecontract();
        ReflectionTestUtils.setField(res, "id", IDS.incrementAndGet());
        res.setEmployee(employee);
        return res;
    }

}
