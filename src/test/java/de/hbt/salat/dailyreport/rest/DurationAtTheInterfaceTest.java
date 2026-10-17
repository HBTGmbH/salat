package de.hbt.salat.dailyreport.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static de.hbt.salat.dailyreport.domain.Workingday.WorkingDayType.WORKED;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.dailyreport.domain.Workingday;
import de.hbt.salat.dailyreport.service.BookingOrderResolver;
import de.hbt.salat.dailyreport.service.DailyWorkingReportService;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.dailyreport.service.WorkingdayService;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.service.EmployeeorderService;

/**
 * Durations and starts at the REST interfaces of the module (#1398), over the web layer: whether a value that is no
 * duration HH:mm or no time of day is answered with 400 is decided while Spring reads the request, before any line
 * of the endpoint runs. A unit test of the endpoint does not reach that place.
 */
class DurationAtTheInterfaceTest {

    private final EmployeeService employeeService = mock(EmployeeService.class);
    private final EmployeecontractService employeecontractService = mock(EmployeecontractService.class);
    private final WorkingdayService workingdayService = mock(WorkingdayService.class);
    private final DailyWorkingReportService dailyWorkingReportService = mock(DailyWorkingReportService.class);
    private final AuthorizedUser authorizedUser = mock(AuthorizedUser.class);
    private final AuthorizedEmployee authorizedEmployee = mock(AuthorizedEmployee.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(authorizedUser.isAuthenticated()).thenReturn(true);
        when(authorizedEmployee.getSign()).thenReturn("tst");
        var employee = new Employee();
        employee.setSign("tst");
        ReflectionTestUtils.setField(employee, "id", 1L);
        when(employeeService.getEmployeeBySign("tst")).thenReturn(employee);
        var employeecontract = new Employeecontract();
        ReflectionTestUtils.setField(employeecontract, "id", 2L);
        when(employeecontractService.getEmployeeContractValidAt(anyLong(), any(LocalDate.class))).thenReturn(employeecontract);

        mockMvc = MockMvcBuilders
            .standaloneSetup(
                new WorkingDayRestEndpoint(employeeService, employeecontractService, workingdayService, authorizedUser,
                    authorizedEmployee),
                new DailyReportRestEndpoint(employeecontractService, mock(EmployeeorderService.class),
                    mock(TimereportService.class), dailyWorkingReportService, mock(BookingOrderResolver.class),
                    authorizedUser, authorizedEmployee),
                new DailyWorkingReportRestEndpoint(employeecontractService, mock(TimereportService.class),
                    workingdayService, dailyWorkingReportService, authorizedUser, authorizedEmployee))
            .setMessageConverters(new DailyReportCsvConverter(),
                new DailyWorkingReportCsvConverter(mock(BookingOrderResolver.class), authorizedUser),
                new JacksonJsonHttpMessageConverter())
            .build();
    }

    @Test
    void the_working_day_is_read_with_both_the_new_and_the_former_fields() throws Exception {
        var workingDay = new Workingday();
        workingDay.setRefday(LocalDate.of(2026, 10, 6));
        workingDay.setStartTime(LocalTime.of(8, 30));
        workingDay.setBreakLength(Duration.ofMinutes(45));
        workingDay.setType(WORKED);
        when(workingdayService.getWorkingday(anyLong(), any(LocalDate.class))).thenReturn(workingDay);

        mockMvc.perform(get("/api/workingday/.me/2026-10-06"))
            .andExpect(status().isOk())
            .andExpect(content().json("""
                {"date":"2026-10-06","startTime":"08:30","breakDuration":"00:45","type":"WORKED",
                 "starthour":8,"startminute":30,"breakhours":0,"breakminutes":45}
                """));
    }

    @Test
    void the_working_day_is_written_with_the_new_fields() throws Exception {
        var stored = upsert("""
            {"date":"2026-10-06","startTime":"08:30","breakDuration":"25:30","type":"WORKED"}
            """);

        assertThat(stored.getStartTime()).isEqualTo(LocalTime.of(8, 30));
        assertThat(stored.getBreakLength()).isEqualTo(Duration.ofHours(25).plusMinutes(30));
    }

    @Test
    void the_working_day_is_written_with_the_former_fields() throws Exception {
        var stored = upsert("""
            {"date":"2026-10-06","starthour":7,"startminute":15,"breakhours":1,"breakminutes":5,"type":"WORKED"}
            """);

        assertThat(stored.getStartTime()).isEqualTo(LocalTime.of(7, 15));
        assertThat(stored.getBreakLength()).isEqualTo(Duration.ofHours(1).plusMinutes(5));
    }

    @Test
    void the_new_fields_of_the_working_day_win_over_the_former() throws Exception {
        var stored = upsert("""
            {"date":"2026-10-06","startTime":"08:30","breakDuration":"00:45",
             "starthour":7,"startminute":15,"breakhours":1,"breakminutes":5,"type":"WORKED"}
            """);

        assertThat(stored.getStartTime()).isEqualTo(LocalTime.of(8, 30));
        assertThat(stored.getBreakLength()).isEqualTo(Duration.ofMinutes(45));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "\"startTime\":\"24:00\"",
        "\"startTime\":\"08:60\"",
        "\"startTime\":\"8\"",
        "\"breakDuration\":\"00:60\"",
        "\"breakDuration\":\"-00:30\"",
        "\"breakDuration\":\"PT30M\"",
        "\"starthour\":24",
        "\"startminute\":60",
        "\"breakminutes\":60",
    })
    void a_working_day_that_is_no_time_of_day_or_no_duration_is_answered_with_400(String field) throws Exception {
        mockMvc.perform(put("/api/workingday/.me").contentType(APPLICATION_JSON)
                .content("{\"date\":\"2026-10-06\",\"type\":\"WORKED\"," + field + "}"))
            .andExpect(status().isBadRequest());

        verify(workingdayService, never()).upsertWorkingday(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"01:60", "-01:00", "1", "PT1H"})
    void a_booking_whose_duration_is_no_duration_is_answered_with_400(String duration) throws Exception {
        mockMvc.perform(post("/api/daily-reports/").contentType(APPLICATION_JSON)
                .content("{\"date\":\"2026-10-06\",\"employeeorderId\":1,\"duration\":\"" + duration + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "\"startTime\":\"08:00\",\"breakDuration\":\"00:60\"",
        "\"startTime\":\"08:00\",\"breakDuration\":\"-00:30\"",
        "\"startTime\":\"08:00\",\"breakDuration\":\"0:3\"",
        "\"startTime\":\"24:00\",\"breakDuration\":\"00:30\"",
    })
    void a_working_report_whose_start_or_break_is_invalid_is_answered_with_400(String fields) throws Exception {
        mockMvc.perform(post("/api/daily-working-reports/").contentType(APPLICATION_JSON)
                .content("{\"date\":\"2026-10-06\",\"type\":\"WORKED\"," + fields + ",\"dailyReports\":[]}"))
            .andExpect(status().isBadRequest());

        verify(dailyWorkingReportService, never()).createReports(any());
    }

    private Workingday upsert(String json) throws Exception {
        mockMvc.perform(put("/api/workingday/.me").contentType(APPLICATION_JSON).content(json))
            .andExpect(status().isCreated());
        var captor = ArgumentCaptor.forClass(Workingday.class);
        verify(workingdayService).upsertWorkingday(captor.capture());
        return captor.getValue();
    }
}
