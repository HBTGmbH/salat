package org.tb.dailyreport.rest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static org.tb.dailyreport.rest.DailyWorkingReportCsvConverterTest.DailyWorkingReportDataFixtures.TWO_BOOKINGS;
import static org.tb.dailyreport.rest.DailyWorkingReportCsvConverterTest.DailyWorkingReportDataFixtures.TWO_BOOKINGS_NO_EMPLOYEE_ORDER;
import static org.tb.dailyreport.rest.DailyWorkingReportCsvConverterTest.DailyWorkingReportDataFixtures.TWO_BOOKINGS_NO_START_BREAK_TIME;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.http.HttpOutputMessage;
import org.springframework.test.util.ReflectionTestUtils;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.dailyreport.domain.Workingday;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.domain.Employee;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.EmployeeorderService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
class DailyWorkingReportCsvConverterTest {

    @Mock
    HttpOutputMessage httpOutputMessage;

    @Mock
    EmployeeorderService employeeorderService;

    @Mock
    AuthorizedEmployee authorizedEmployee;

    @Mock
    AuthorizedUser authorizedUser;

    @InjectMocks
    DailyWorkingReportCsvConverter dailyWorkingReportCsvConverter;

    private static Stream<Arguments> readCsv() {
        return Stream.of(
            Arguments.of(
                "",
                List.of(),
                false
            ),
            Arguments.of(
                """
                    date,type,startTime,breakTime,employeeorderId,orderSign,orderLabel,suborderSign,suborderLabel,workingTime,comment
                    ,,,,,,,,,,some comment
                    2024-11-04,WORKED,09:00,00:30,183209,111,Rumsitzen,111/01,Stuhlpolsterung,00:30,Team-Mittag
                    2024-11-04,,,,183209,111,Rumsitzen,111/01,Stuhlpolsterung,07:30,Daily
                    """,
                List.of(TWO_BOOKINGS),
                false
            ),
            Arguments.of(
                """
                    date;type;startTime;breakTime;employeeorderId;orderSign;orderLabel;suborderSign;suborderLabel;workingTime;comment
                    2024-11-04;WORKED;09:00;00:30;183209;111;Rumsitzen;111/01;Stuhlpolsterung;00:30;Team-Mittag
                    2024-11-04;;;;183209;111;Rumsitzen;111/01;Stuhlpolsterung;07:30;Daily
                    """,
                List.of(TWO_BOOKINGS),
                false
            ),
            Arguments.of(
                """
                    date;type;startTime;breakTime;employeeorderId;orderSign;orderLabel;suborderSign;suborderLabel;workingTime;comment
                    04.11.2024;WORKED;09:00:00;00:30:00;183209;111;Rumsitzen;111/01;Stuhlpolsterung;00:30:00;Team-Mittag
                    04.11.2024;;;;183209;111;Rumsitzen;111/01;Stuhlpolsterung;07:30:00;Daily
                    """,
                List.of(TWO_BOOKINGS),
                false
            ),
            Arguments.of(
                """
                    date;type;startTime;breakTime;suborderSign;workingTime;comment
                    2024-11-04;WORKED;09:00;00:30;111/01;00:30;Team-Mittag
                    2024-11-04;;;;111/01;07:30;Daily
                    """,
                List.of(TWO_BOOKINGS_NO_EMPLOYEE_ORDER),
                false
            ),
            Arguments.of(
                """
                    date;type;startTime;breakTime;employeeorderId;orderSign;orderLabel;suborderSign;suborderLabel;workingTime;comment
                    04.11.2024;WORKED;;;183209;111;Rumsitzen;111/01;Stuhlpolsterung;00:30:00;Team-Mittag
                    04.11.2024;;;;183209;111;Rumsitzen;111/01;Stuhlpolsterung;07:30:00;Daily
                    """,
                List.of(TWO_BOOKINGS_NO_START_BREAK_TIME),
                true
            )
        );
    }

    @ParameterizedTest
    @MethodSource("readCsv")
    void shouldReadFromCsv(String csv, List<DailyWorkingReportData> expected, boolean restricted) throws IOException {
        // given
        var customerorder = new Customerorder();
        customerorder.setSign("111");
        customerorder.setDescription("Rumsitzen");
        var suborder = new Suborder();
        suborder.setSign("01");
        suborder.setDescription("Stuhlpolsterung");
        suborder.setCustomerorder(customerorder);
        var employee = new Employee();
        ReflectionTestUtils.setField(employee, "id", 42L);
        employee.setSign("testuser");
        var employeeorder = new Employeeorder();
        ReflectionTestUtils.setField(employeeorder, "id", 183209L);
        employeeorder.setSuborder(suborder);

        when(employeeorderService.getEmployeeorderById(eq(183209L))).thenReturn(employeeorder);
        when(employeeorderService.getEmployeeorderByEmployeeAndSuborder(eq("testuser"), eq("111/01"), any())).thenReturn(employeeorder);
        when(authorizedUser.getLoginSign()).thenReturn("testuser");
        when(authorizedUser.isRestricted()).thenReturn(restricted);
        when(authorizedEmployee.getSign()).thenReturn(employee.getSign());

        // when
        var result = dailyWorkingReportCsvConverter.read(IOUtils.toInputStream(csv, UTF_8));

        // then
        assertThat(result).containsExactlyInAnyOrderElementsOf(expected);
    }

    private static Stream<Arguments> writeCsv() {
        return Stream.of(
            Arguments.of(
                    List.of(TWO_BOOKINGS),
                    """
                    date,type,startTime,breakTime,employeeorderId,orderSign,orderLabel,suborderSign,suborderLabel,workingTime,comment
                    2024-11-04,WORKED,09:00,00:30,183209,111,Rumsitzen,111/01,Stuhlpolsterung,00:30,Team-Mittag
                    2024-11-04,,,,183209,111,Rumsitzen,111/01,Stuhlpolsterung,07:30,Daily
                    """,
                    false
            ),
            Arguments.of(
                List.of(TWO_BOOKINGS_NO_START_BREAK_TIME),
                """
                date,type,startTime,breakTime,employeeorderId,orderSign,orderLabel,suborderSign,suborderLabel,workingTime,comment
                2024-11-04,WORKED,,,183209,111,Rumsitzen,111/01,Stuhlpolsterung,00:30,Team-Mittag
                2024-11-04,,,,183209,111,Rumsitzen,111/01,Stuhlpolsterung,07:30,Daily
                """,
                true
            )
        );
    }

    @ParameterizedTest
    @MethodSource("writeCsv")
    void shouldWriteToCsv(List<DailyWorkingReportData> reportData, String expected, boolean restricted) throws IOException {
        // given
        var outputStream = new ByteArrayOutputStream();
        when(httpOutputMessage.getBody()).thenReturn(outputStream);
        when(authorizedUser.isRestricted()).thenReturn(restricted);

        // when
        dailyWorkingReportCsvConverter.write(reportData, null, httpOutputMessage);

        // then
        assertThat(outputStream.toString(UTF_8)).isEqualTo(expected);
    }

    class DailyWorkingReportDataFixtures {
      static DailyWorkingReportData ONE_BOOKING = DailyWorkingReportData.builder()
          .type(Workingday.WorkingDayType.WORKED)
          .date(LocalDate.of(2024,11,3))
          .startTime(LocalTime.of(8,0))
          .breakDuration(LocalTime.of(0,45))
          .dailyReports(List.of(
              DailyReportData.builder()
                  .date("2024-11-03")
                  .employeeorderId(183209)
                  .orderSign("111")
                  .orderLabel("Rumsitzen")
                  .suborderSign("111/01")
                  .suborderLabel("Stuhlpolsterung")
                  .hours(8)
                  .minutes(0)
                  .comment("schlafen")
                  .build()
          ))
          .build();
      static DailyWorkingReportData TWO_BOOKINGS = DailyWorkingReportData.builder()
          .type(Workingday.WorkingDayType.WORKED)
          .date(LocalDate.of(2024,11,4))
          .startTime(LocalTime.of(9,0))
          .breakDuration(LocalTime.of(0,30))
          .dailyReports(List.of(
              DailyReportData.builder()
                  .date("2024-11-04")
                  .employeeorderId(183209)
                  .orderSign("111")
                  .orderLabel("Rumsitzen")
                  .suborderSign("111/01")
                  .suborderLabel("Stuhlpolsterung")
                  .hours(0)
                  .minutes(30)
                  .comment("Team-Mittag")
                  .build(),
              DailyReportData.builder()
                  .date("2024-11-04")
                  .employeeorderId(183209)
                  .orderSign("111")
                  .orderLabel("Rumsitzen")
                  .suborderSign("111/01")
                  .suborderLabel("Stuhlpolsterung")
                  .hours(7)
                  .minutes(30)
                  .comment("Daily")
                  .build()
          ))
          .build();
        static DailyWorkingReportData TWO_BOOKINGS_NO_START_BREAK_TIME = DailyWorkingReportData.builder()
            .type(Workingday.WorkingDayType.WORKED)
            .date(LocalDate.of(2024,11,4))
            .dailyReports(List.of(
                DailyReportData.builder()
                    .date("2024-11-04")
                    .employeeorderId(183209)
                    .orderSign("111")
                    .orderLabel("Rumsitzen")
                    .suborderSign("111/01")
                    .suborderLabel("Stuhlpolsterung")
                    .hours(0)
                    .minutes(30)
                    .comment("Team-Mittag")
                    .build(),
                DailyReportData.builder()
                    .date("2024-11-04")
                    .employeeorderId(183209)
                    .orderSign("111")
                    .orderLabel("Rumsitzen")
                    .suborderSign("111/01")
                    .suborderLabel("Stuhlpolsterung")
                    .hours(7)
                    .minutes(30)
                    .comment("Daily")
                    .build()
            ))
            .build();
      static DailyWorkingReportData TWO_BOOKINGS_NO_EMPLOYEE_ORDER = DailyWorkingReportData.builder()
        .type(Workingday.WorkingDayType.WORKED)
        .date(LocalDate.of(2024,11,4))
        .startTime(LocalTime.of(9,0))
        .breakDuration(LocalTime.of(0,30))
        .dailyReports(List.of(
            DailyReportData.builder()
                .date("2024-11-04")
                .employeeorderId(183209)
                .orderSign("111")
                .orderLabel("Rumsitzen")
                .suborderSign("111/01")
                .suborderLabel("Stuhlpolsterung")
                .hours(0)
                .minutes(30)
                .comment("Team-Mittag")
                .build(),
            DailyReportData.builder()
                .date("2024-11-04")
                .employeeorderId(183209)
                .orderSign("111")
                .orderLabel("Rumsitzen")
                .suborderSign("111/01")
                .suborderLabel("Stuhlpolsterung")
                .hours(7)
                .minutes(30)
                .comment("Daily")
                .build()
        ))
        .build();
    }

}