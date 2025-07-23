package org.tb.dailyreport.rest;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.WORKED;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.service.WorkingdayService;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;

@ExtendWith(MockitoExtension.class)
class WorkingDayRestEndpointTest {
    @Mock
    EmployeecontractService employeecontractService;

    @Mock
    WorkingdayService workingdayService;

    @Mock
    AuthorizedUser authorizedUser;

    @Mock
    EmployeeService employeeService;

    @Captor
    ArgumentCaptor<Workingday> workingDayArgumentCaptor;

    @InjectMocks
    WorkingDayRestEndpoint workingDayRestEndpoint;

    @BeforeEach
    void initAuthorizedUser() {
        when(authorizedUser.isAuthenticated()).thenReturn(true);
    }

    @Test
    void shouldCreateWorkingDay() {
        // given
        var employeeContract = employeeContract();
        var data = WorkingDayData.builder()
                .starthour(7)
                .startminute(8)
                .breakhours(9)
                .breakminutes(10)
                .date("2024-07-06")
                .type(WORKED)
                .build();
        var employee = new Employee();
        employee.setSign("test");
        ReflectionTestUtils.setField(employee, "id", 1L);
        when(employeeService.getEmployeeBySign("test")).thenReturn(employee);
        when(employeecontractService.getEmployeeContractValidAt(anyLong(), any(LocalDate.class)))
                .thenReturn(employeeContract);

        // when
        workingDayRestEndpoint.upsert(data, "test");

        // then
        verify(workingdayService, times(1)).upsertWorkingday(workingDayArgumentCaptor.capture());
        assertThat(workingDayArgumentCaptor.getValue())
                .isNotNull()
                .hasFieldOrPropertyWithValue("Starttimehour", 7)
                .hasFieldOrPropertyWithValue("Starttimeminute", 8)
                .hasFieldOrPropertyWithValue("Breakhours", 9)
                .hasFieldOrPropertyWithValue("Breakminutes", 10)
                .hasFieldOrPropertyWithValue("refday", DateUtils.parse("2024-07-06"))
                .hasFieldOrPropertyWithValue("type", WORKED)
                .hasFieldOrPropertyWithValue("employeecontract", employeeContract);
    }

    @Test
    void shouldGetWorkingDay() {
        // given
        var employeeContract = employeeContract();
        var workingDay = new Workingday();
        workingDay.setStarttimehour(7);
        workingDay.setRefday(DateUtils.parse("2024-07-06"));
        when(employeecontractService.getEmployeeContractValidAt(anyLong(), any(LocalDate.class)))
                .thenReturn(employeeContract);
        when(workingdayService.getWorkingday(anyLong(), any(LocalDate.class)))
                .thenReturn(workingDay);
        var employee = new Employee();
        employee.setSign("test");
        ReflectionTestUtils.setField(employee, "id", 1L);
        when(employeeService.getEmployeeBySign("test")).thenReturn(employee);

        // when
        var result = workingDayRestEndpoint.get("test", "2024-07-06");

        // then
        assertThat(result)
                .isNotNull()
                .hasFieldOrPropertyWithValue("starthour", 7)
                .hasFieldOrPropertyWithValue("date", "2024-07-06");
    }

    // fixtures

    private Employeecontract employeeContract() {
        Employeecontract res = new Employeecontract();
        ReflectionTestUtils.setField(res, "id", (long) (Math.random() * 100));
        return res;
    }
}