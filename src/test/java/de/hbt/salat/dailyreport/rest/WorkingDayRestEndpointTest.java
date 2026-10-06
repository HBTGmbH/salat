package de.hbt.salat.dailyreport.rest;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static de.hbt.salat.dailyreport.domain.Workingday.WorkingDayType.WORKED;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.dailyreport.domain.Workingday;
import de.hbt.salat.dailyreport.service.WorkingdayService;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.service.EmployeecontractService;

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
                .hasFieldOrPropertyWithValue("startTime", LocalTime.of(7, 8))
                .hasFieldOrPropertyWithValue("breakLength", Duration.ofHours(9).plusMinutes(10))
                .hasFieldOrPropertyWithValue("refday", DateUtils.parse("2024-07-06"))
                .hasFieldOrPropertyWithValue("type", WORKED)
                .hasFieldOrPropertyWithValue("employeecontract", employeeContract);
    }

    /**
     * The fields are plain numbers: what is no time of day or no break is the client's mistake,
     * answered with 400 — not the 500 of an exception nothing maps.
     */
    @ParameterizedTest
    @CsvSource({
        "24, 0, 0, 30",
        "8, 60, 0, 30",
        "-1, 0, 0, 30",
        "8, 0, -1, 0",
        "8, 0, 0, -5",
        "8, 0, 0, 60",
    })
    void shouldRejectStartOrBreakOutOfRange(int starthour, int startminute, int breakhours, int breakminutes) {
        // given
        var data = WorkingDayData.builder()
                .starthour(starthour)
                .startminute(startminute)
                .breakhours(breakhours)
                .breakminutes(breakminutes)
                .date("2024-07-06")
                .type(WORKED)
                .build();
        var employee = new Employee();
        employee.setSign("test");
        ReflectionTestUtils.setField(employee, "id", 1L);
        when(employeeService.getEmployeeBySign("test")).thenReturn(employee);
        when(employeecontractService.getEmployeeContractValidAt(anyLong(), any(LocalDate.class)))
                .thenReturn(employeeContract());

        // when / then
        assertThatThrownBy(() -> workingDayRestEndpoint.upsert(data, "test"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(workingdayService, never()).upsertWorkingday(any());
    }

    @Test
    void shouldGetWorkingDay() {
        // given
        var employeeContract = employeeContract();
        var workingDay = new Workingday();
        workingDay.setStartTime(LocalTime.of(7, 0));
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

    // deterministic, collision-free ids for fixtures (counter resets per test instance)
    private long nextId = 1;

    private long nextId() {
        return nextId++;
    }

    private Employeecontract employeeContract() {
        Employeecontract res = new Employeecontract();
        ReflectionTestUtils.setField(res, "id", nextId());
        return res;
    }
}