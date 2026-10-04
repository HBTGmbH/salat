package de.hbt.salat.dailyreport.rest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static de.hbt.salat.dailyreport.rest.DailyWorkingReportCsvConverterTest.DailyWorkingReportDataFixtures.TWO_BOOKINGS;
import static de.hbt.salat.dailyreport.rest.DailyWorkingReportCsvConverterTest.DailyWorkingReportDataFixtures.TWO_BOOKINGS_NO_EMPLOYEE_ORDER;
import static de.hbt.salat.dailyreport.rest.DailyWorkingReportCsvConverterTest.DailyWorkingReportDataFixtures.TWO_BOOKINGS_NO_START_BREAK_TIME;
import static de.hbt.salat.dailyreport.rest.DailyWorkingReportCsvConverterTest.DailyWorkingReportDataFixtures.TWO_BOOKINGS_WITH_TICKET_REFERENCE;
import static de.hbt.salat.dailyreport.rest.DailyWorkingReportCsvConverterTest.DailyWorkingReportDataFixtures.TWO_BOOKINGS_WITH_TWO_TICKET_REFERENCES;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;
import de.hbt.salat.dailyreport.domain.Workingday;
import de.hbt.salat.dailyreport.service.BookingOrderResolver;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.Suborder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
class DailyWorkingReportCsvConverterTest {

    @Mock
    HttpOutputMessage httpOutputMessage;

    @Mock
    BookingOrderResolver bookingOrderResolver;

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
            ),
            Arguments.of(
                """
                    date,type,startTime,breakTime,employeeorderId,orderSign,orderLabel,suborderSign,suborderLabel,workingTime,comment,ticketReference,training
                    2024-11-04,WORKED,09:00,00:30,183209,111,Rumsitzen,111/01,Stuhlpolsterung,00:30,Team-Mittag,ERP-1,
                    2024-11-04,,,,183209,111,Rumsitzen,111/01,Stuhlpolsterung,07:30,Daily,,TRUE
                    """,
                List.of(TWO_BOOKINGS_WITH_TICKET_REFERENCE),
                false
            )
        );
    }

    /* Over the REST API the file is read as it stands; the service assigns the orders (#1142). */
    @ParameterizedTest
    @MethodSource("readCsv")
    void shouldReadFromCsv(String csv, List<DailyWorkingReportData> expected, boolean restricted) throws IOException {
        // given
        when(authorizedUser.isRestricted()).thenReturn(restricted);

        // when
        var result = dailyWorkingReportCsvConverter.read(IOUtils.toInputStream(csv, UTF_8)).reports();

        // then
        assertThat(result).containsExactlyInAnyOrderElementsOf(expected);
        verifyNoInteractions(bookingOrderResolver);
    }

    /* The UI import assigns each booking its order while the line is still known, and takes over what
       follows from the order - whatever the file said about it (#1142). */
    @Test
    void assigns_each_booking_of_the_ui_import_its_order() throws IOException {
        var csv = """
            date,type,startTime,breakTime,suborderSign,workingTime,comment,employeeSign
            2024-11-04,WORKED,09:00,00:30,111/01,00:30,Team-Mittag,testuser
            """;
        var employee = employee();
        when(bookingOrderResolver.resolveFor(eq(employee), any(), eq(LocalDate.of(2024, 11, 4)))).thenReturn(employeeorder());

        var result = dailyWorkingReportCsvConverter.read(IOUtils.toInputStream(csv, UTF_8), employee).reports();

        assertThat(result).singleElement().satisfies(day ->
            assertThat(day.getDailyReports()).singleElement().satisfies(booking -> {
                assertThat(booking.getEmployeeorderId()).isEqualTo(183209L);
                assertThat(booking.getOrderSign()).isEqualTo("111");
                assertThat(booking.getOrderLabel()).isEqualTo("Rumsitzen");
                assertThat(booking.getSuborderSign()).isEqualTo("111/01");
                assertThat(booking.getSuborderLabel()).isEqualTo("Stuhlpolsterung");
                assertThat(booking.getEmployeeSign()).isEqualTo("testuser");
            }));
        verify(bookingOrderResolver).resolveFor(eq(employee), argThat(booking ->
            booking.getEmployeeorderId() == null && "111/01".equals(booking.getSuborderSign())
                && "testuser".equals(booking.getEmployeeSign())), eq(LocalDate.of(2024, 11, 4)));
    }

    /* A booking that cannot be assigned is reported with its line, wrapping the reason the REST API
       reports without one (#1142). */
    @Test
    void reports_a_booking_that_cannot_be_assigned_with_its_line() {
        var csv = """
            date,type,startTime,breakTime,suborderSign,workingTime,comment
            2024-11-04,WORKED,09:00,00:30,111/01,00:30,Team-Mittag
            2024-11-05,WORKED,09:00,00:30,111/1,00:30,Daily
            """;
        var employee = employee();
        when(bookingOrderResolver.resolveFor(eq(employee), any(), eq(LocalDate.of(2024, 11, 4)))).thenReturn(employeeorder());
        when(bookingOrderResolver.resolveFor(eq(employee), any(), eq(LocalDate.of(2024, 11, 5))))
            .thenThrow(new InvalidDataException(ErrorCode.TR_BOOKING_NO_EMPLOYEE_ORDER, "111/1", "testuser", "2024-11-05"));

        assertThatThrownBy(() -> dailyWorkingReportCsvConverter.read(IOUtils.toInputStream(csv, UTF_8), employee))
            .isInstanceOfSatisfying(InvalidDataException.class, ex ->
                assertThat(ex.getMessages()).singleElement().satisfies(message -> {
                    assertThat(message.getErrorCode()).isEqualTo(ErrorCode.TR_CSV_LINE_REJECTED);
                    assertThat(message.getArguments()).hasSize(2).first().isEqualTo(3L);
                    assertThat(message.getArguments().get(1)).isInstanceOfSatisfying(ServiceFeedbackMessage.class, reason ->
                        assertThat(reason.getErrorCode()).isEqualTo(ErrorCode.TR_BOOKING_NO_EMPLOYEE_ORDER));
                }));
    }

    /* A comment in quotes may span several lines; the line reported is the one the booking begins in. */
    @Test
    void counts_the_lines_of_a_comment_spanning_several() {
        var csv = """
            date,type,startTime,breakTime,suborderSign,workingTime,comment
            2024-11-04,WORKED,09:00,00:30,111/01,00:30,"erste Zeile
            zweite Zeile"
            2024-11-05,WORKED,09:00,00:30,111/1,00:30,Daily
            """;
        var employee = employee();
        when(bookingOrderResolver.resolveFor(eq(employee), any(), eq(LocalDate.of(2024, 11, 4)))).thenReturn(employeeorder());
        when(bookingOrderResolver.resolveFor(eq(employee), any(), eq(LocalDate.of(2024, 11, 5))))
            .thenThrow(new InvalidDataException(ErrorCode.TR_BOOKING_NO_EMPLOYEE_ORDER, "111/1", "testuser", "2024-11-05"));

        assertThatThrownBy(() -> dailyWorkingReportCsvConverter.read(IOUtils.toInputStream(csv, UTF_8), employee))
            .isInstanceOfSatisfying(InvalidDataException.class, ex ->
                assertThat(ex.getMessages().getFirst().getArguments().getFirst()).isEqualTo(4L));
    }

    /* A row with a duration is a booking even without an order; it used to be dropped without a word. */
    @Test
    void hands_a_booking_without_an_order_on_instead_of_dropping_it() throws IOException {
        var csv = """
            date,type,startTime,breakTime,workingTime,comment
            2024-11-04,WORKED,09:00,00:30,00:30,Team-Mittag
            """;

        var result = dailyWorkingReportCsvConverter.read(IOUtils.toInputStream(csv, UTF_8)).reports();

        assertThat(result.getFirst().getDailyReports()).singleElement().satisfies(booking -> {
            assertThat(booking.getEmployeeorderId()).isNull();
            assertThat(booking.getSuborderSign()).isNull();
        });
    }

    /**
     * Die Datei, an der sich #1112 zeigt: ein einziger Wert passt in keines der erwarteten Formate.
     * Alles andere an der Zeile ist lesbar - und nichts davon gehört in die Rückmeldung.
     */
    private static String csvWithUnreadable(String date, String startTime) {
        return """
            date,type,startTime,breakTime,employeeorderId,orderSign,orderLabel,suborderSign,suborderLabel,workingTime,comment
            %s,WORKED,%s,00:30,183209,111,Rumsitzen,111/01,Stuhlpolsterung,00:30,Team-Mittag
            """.formatted(date, startTime);
    }

    /* Ein nicht lesbares Datum ist ein Eingabefehler der hochgeladenen Datei, kein Systemfehler:
       gemeldet werden Zeile, Spalte, der einzelne Wert und die erwarteten Formate (#1112). */
    @Test
    void reports_an_unreadable_date_as_an_input_error() {
        var csv = csvWithUnreadable("04/11/2024", "09:00");

        assertThatThrownBy(() -> dailyWorkingReportCsvConverter.read(IOUtils.toInputStream(csv, UTF_8)))
            .isInstanceOfSatisfying(InvalidDataException.class, ex ->
                assertThat(ex.getMessages()).singleElement().satisfies(message -> {
                    assertThat(message.getErrorCode()).isEqualTo(ErrorCode.TR_CSV_VALUE_FORMAT_INVALID);
                    assertThat(message.getArguments())
                        .containsExactly(2L, "date", "04/11/2024", "yyyy-MM-dd, dd.MM.yyyy");
                }));
    }

    /* Derselbe Ausgang für eine Uhrzeit - das ist der Wert, der im Betrieb aufgefallen ist. */
    @Test
    void reports_an_unreadable_time_as_an_input_error() {
        var csv = csvWithUnreadable("2024-11-04", "9 Uhr");

        assertThatThrownBy(() -> dailyWorkingReportCsvConverter.read(IOUtils.toInputStream(csv, UTF_8)))
            .isInstanceOfSatisfying(InvalidDataException.class, ex ->
                assertThat(ex.getMessages()).singleElement().satisfies(message -> {
                    assertThat(message.getErrorCode()).isEqualTo(ErrorCode.TR_CSV_VALUE_FORMAT_INVALID);
                    assertThat(message.getArguments())
                        .containsExactly(2L, "startTime", "9 Uhr", "HH:mm, HH:mm:ss");
                }));
    }

    /* Die Rohzeile bleibt draußen - weder der Kommentar noch die Auftragsbezeichnungen der Zeile
       dürfen in der Rückmeldung oder im Log stehen (#1112). */
    @Test
    void keeps_the_faulty_line_out_of_what_it_hands_on() {
        var csv = csvWithUnreadable("2024-11-04", "9 Uhr");

        assertThatThrownBy(() -> dailyWorkingReportCsvConverter.read(IOUtils.toInputStream(csv, UTF_8)))
            .isInstanceOfSatisfying(InvalidDataException.class, ex ->
                assertThat(ex.getMessages().getFirst().getArguments())
                    .doesNotContain("Team-Mittag", "Stuhlpolsterung", "Rumsitzen", "183209"));
    }

    /**
     * Der Verarbeitungspool von opencsv reichte die Ausnahme durch seinen Worker-Thread hindurch:
     * der Thread starb mit einem Stacktrace auf der Konsole, und der Aufrufer bekam denselben
     * Fehler ein zweites Mal (#1112). Gesammelt statt geworfen, stirbt dort nichts mehr.
     */
    @Test
    void leaves_no_thread_dying_with_an_uncaught_exception() {
        var uncaught = new CopyOnWriteArrayList<Throwable>();
        var previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> uncaught.add(throwable));
        try {
            var csv = csvWithUnreadable("2024-11-04", "9 Uhr");
            assertThatThrownBy(() -> dailyWorkingReportCsvConverter.read(IOUtils.toInputStream(csv, UTF_8)))
                .isInstanceOf(InvalidDataException.class);
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous);
        }

        assertThat(uncaught).isEmpty();
    }

    /* A file without the column says nothing about the reference; the service must be able to tell
       that from an empty column (#1140). */
    @Test
    void leaves_the_reference_unknown_when_the_column_is_missing() throws IOException {
        var csv = """
            date,type,startTime,breakTime,employeeorderId,workingTime,comment
            2024-11-04,WORKED,09:00,00:30,183209,00:30,Team-Mittag
            """;

        var result = dailyWorkingReportCsvConverter.read(IOUtils.toInputStream(csv, UTF_8)).reports();

        assertThat(result).singleElement().satisfies(day ->
            assertThat(day.getDailyReports()).singleElement().satisfies(booking ->
                assertThat(booking).satisfies(
                    b -> assertThat(b.getTicketReference()).isNull(),
                    b -> assertThat(b.isTraining()).isFalse())));
    }

    /* A reference longer than a booking can store is reported like an unreadable value, with line
       and column, before anything is saved (#1140). */
    @Test
    void reports_a_too_long_ticket_reference_with_line_and_column() {
        var csv = """
            date,type,startTime,breakTime,employeeorderId,workingTime,comment,ticketReference
            2024-11-04,WORKED,09:00,00:30,183209,00:30,Team-Mittag,%s
            """.formatted("X".repeat(65));

        assertThatThrownBy(() -> dailyWorkingReportCsvConverter.read(IOUtils.toInputStream(csv, UTF_8)))
            .isInstanceOfSatisfying(InvalidDataException.class, ex ->
                assertThat(ex.getMessages()).singleElement().satisfies(message -> {
                    assertThat(message.getErrorCode()).isEqualTo(ErrorCode.TR_CSV_VALUE_TOO_LONG);
                    assertThat(message.getArguments()).containsExactly(2L, "ticketReference", 64);
                }));
    }

    /* Several references share the column, separated by semicolons (#1326); a blank in a reference stays. */
    @Test
    void reads_several_references_separated_by_semicolons() throws IOException {
        var csv = """
            date,type,startTime,breakTime,employeeorderId,workingTime,comment,ticketReference
            2024-11-04,WORKED,09:00,00:30,183209,00:30,Team-Mittag,ERP-1;erp-2; ;otp dev meeting
            """;

        var result = dailyWorkingReportCsvConverter.read(IOUtils.toInputStream(csv, UTF_8)).reports();

        assertThat(result).singleElement().satisfies(day ->
            assertThat(day.getDailyReports()).singleElement().satisfies(booking -> {
                assertThat(booking.getTicketReferences()).containsExactly("ERP-1", "erp-2", "otp dev meeting");
                assertThat(booking.givenTicketReferences()).containsExactly("ERP-1", "erp-2", "otp dev meeting");
            }));
    }

    /* Each reference has to fit the column, not the cell as a whole (#1326). */
    @Test
    void checks_the_length_of_each_reference_in_the_cell() {
        var csv = """
            date,type,startTime,breakTime,employeeorderId,workingTime,comment,ticketReference
            2024-11-04,WORKED,09:00,00:30,183209,00:30,Team-Mittag,%s;%s
            """.formatted("A".repeat(60), "X".repeat(65));

        assertThatThrownBy(() -> dailyWorkingReportCsvConverter.read(IOUtils.toInputStream(csv, UTF_8)))
            .isInstanceOfSatisfying(InvalidDataException.class, ex ->
                assertThat(ex.getMessages()).singleElement().satisfies(message ->
                    assertThat(message.getErrorCode()).isEqualTo(ErrorCode.TR_CSV_VALUE_TOO_LONG)));
    }

    /* The length that counts is the one stored: surrounding blanks are trimmed away first. */
    @Test
    void accepts_a_reference_that_only_exceeds_the_length_by_surrounding_blanks() throws IOException {
        var csv = """
            date,type,startTime,breakTime,employeeorderId,workingTime,comment,ticketReference
            2024-11-04,WORKED,09:00,00:30,183209,00:30,Team-Mittag,"  %s  "
            """.formatted("X".repeat(64));

        var result = dailyWorkingReportCsvConverter.read(IOUtils.toInputStream(csv, UTF_8)).reports();

        assertThat(result.getFirst().getDailyReports().getFirst().getTicketReference().trim()).hasSize(64);
    }

    /* A value that is neither true nor false is reported, not read as false - a typo must not turn
       a training booking into an ordinary one (#1140). */
    @Test
    void reports_an_unreadable_training_flag_as_an_input_error() {
        var csv = """
            date,type,startTime,breakTime,employeeorderId,workingTime,comment,training
            2024-11-04,WORKED,09:00,00:30,183209,00:30,Team-Mittag,ja
            """;

        assertThatThrownBy(() -> dailyWorkingReportCsvConverter.read(IOUtils.toInputStream(csv, UTF_8)))
            .isInstanceOfSatisfying(InvalidDataException.class, ex ->
                assertThat(ex.getMessages()).singleElement().satisfies(message -> {
                    assertThat(message.getErrorCode()).isEqualTo(ErrorCode.TR_CSV_VALUE_FORMAT_INVALID);
                    assertThat(message.getArguments()).containsExactly(2L, "training", "ja", "true, false");
                }));
    }

    /* Over the REST API the same faulty file is the caller's error, answered with 400 (#1140). */
    @Test
    void answers_a_too_long_reference_over_the_api_as_not_readable() throws IOException {
        var csv = """
            date,type,startTime,breakTime,employeeorderId,workingTime,comment,ticketReference
            2024-11-04,WORKED,09:00,00:30,183209,00:30,Team-Mittag,%s
            """.formatted("X".repeat(65));
        var inputMessage = new MockHttpInputMessage(csv.getBytes(UTF_8));

        assertThatThrownBy(() -> dailyWorkingReportCsvConverter.read(null, inputMessage))
            .isInstanceOf(HttpMessageNotReadableException.class);
    }

    private static Employee employee() {
        var employee = new Employee();
        ReflectionTestUtils.setField(employee, "id", 42L);
        employee.setSign("testuser");
        return employee;
    }

    private static Employeeorder employeeorder() {
        var contract = new Employeecontract();
        contract.setEmployee(employee());
        var customerorder = new Customerorder();
        customerorder.setSign("111");
        customerorder.setDescription("Rumsitzen");
        var suborder = new Suborder();
        suborder.setSign("01");
        suborder.setDescription("Stuhlpolsterung");
        suborder.setCustomerorder(customerorder);
        var employeeorder = new Employeeorder();
        ReflectionTestUtils.setField(employeeorder, "id", 183209L);
        employeeorder.setSuborder(suborder);
        employeeorder.setEmployeecontract(contract);
        return employeeorder;
    }

    private static Stream<Arguments> writeCsv() {
        return Stream.of(
            Arguments.of(
                    List.of(TWO_BOOKINGS),
                    """
                    date,type,startTime,breakTime,employeeorderId,orderSign,orderLabel,suborderSign,suborderLabel,workingTime,comment,ticketReference,training,employeeSign
                    2024-11-04,WORKED,09:00,00:30,183209,111,Rumsitzen,111/01,Stuhlpolsterung,00:30,Team-Mittag,,false,
                    2024-11-04,,,,183209,111,Rumsitzen,111/01,Stuhlpolsterung,07:30,Daily,,false,
                    """,
                    false
            ),
            Arguments.of(
                List.of(TWO_BOOKINGS_NO_START_BREAK_TIME),
                """
                date,type,startTime,breakTime,employeeorderId,orderSign,orderLabel,suborderSign,suborderLabel,workingTime,comment,ticketReference,training,employeeSign
                2024-11-04,WORKED,,,183209,111,Rumsitzen,111/01,Stuhlpolsterung,00:30,Team-Mittag,,false,
                2024-11-04,,,,183209,111,Rumsitzen,111/01,Stuhlpolsterung,07:30,Daily,,false,
                """,
                true
            ),
            Arguments.of(
                List.of(TWO_BOOKINGS_WITH_TICKET_REFERENCE),
                """
                date,type,startTime,breakTime,employeeorderId,orderSign,orderLabel,suborderSign,suborderLabel,workingTime,comment,ticketReference,training,employeeSign
                2024-11-04,WORKED,09:00,00:30,183209,111,Rumsitzen,111/01,Stuhlpolsterung,00:30,Team-Mittag,ERP-1,false,
                2024-11-04,,,,183209,111,Rumsitzen,111/01,Stuhlpolsterung,07:30,Daily,,true,
                """,
                false
            ),
            Arguments.of(
                List.of(TWO_BOOKINGS_WITH_TWO_TICKET_REFERENCES),
                """
                date,type,startTime,breakTime,employeeorderId,orderSign,orderLabel,suborderSign,suborderLabel,workingTime,comment,ticketReference,training,employeeSign
                2024-11-04,WORKED,09:00,00:30,183209,111,Rumsitzen,111/01,Stuhlpolsterung,00:30,Team-Mittag,ERP-1;ERP-2,false,
                2024-11-04,,,,183209,111,Rumsitzen,111/01,Stuhlpolsterung,07:30,Daily,,false,
                """,
                false
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
                  .employeeorderId(183209L)
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
                  .employeeorderId(183209L)
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
                  .employeeorderId(183209L)
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
      /* read from a file with both columns: the empty reference is "no reference", not a missing one;
         an empty training flag is false (#1140) */
      static DailyWorkingReportData TWO_BOOKINGS_WITH_TICKET_REFERENCE = DailyWorkingReportData.builder()
          .type(TWO_BOOKINGS.getType())
          .date(TWO_BOOKINGS.getDate())
          .startTime(TWO_BOOKINGS.getStartTime())
          .breakDuration(TWO_BOOKINGS.getBreakDuration())
          .dailyReports(List.of(
              TWO_BOOKINGS.getDailyReports().get(0).withTicketReferences(List.of("ERP-1")),
              TWO_BOOKINGS.getDailyReports().get(1).toBuilder().training(true).build().withTicketReferences(List.of())))
          .build();
      /* several references are written into one cell, separated by semicolons (#1326) */
      static DailyWorkingReportData TWO_BOOKINGS_WITH_TWO_TICKET_REFERENCES = DailyWorkingReportData.builder()
          .type(TWO_BOOKINGS.getType())
          .date(TWO_BOOKINGS.getDate())
          .startTime(TWO_BOOKINGS.getStartTime())
          .breakDuration(TWO_BOOKINGS.getBreakDuration())
          .dailyReports(List.of(
              TWO_BOOKINGS.getDailyReports().get(0).withTicketReferences(List.of("ERP-1", "ERP-2")),
              TWO_BOOKINGS.getDailyReports().get(1)))
          .build();
        static DailyWorkingReportData TWO_BOOKINGS_NO_START_BREAK_TIME = DailyWorkingReportData.builder()
            .type(Workingday.WorkingDayType.WORKED)
            .date(LocalDate.of(2024,11,4))
            .dailyReports(List.of(
                DailyReportData.builder()
                    .date("2024-11-04")
                    .employeeorderId(183209L)
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
                    .employeeorderId(183209L)
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
      /* read as it stands: no id, no labels - the service assigns the order (#1142) */
      static DailyWorkingReportData TWO_BOOKINGS_NO_EMPLOYEE_ORDER = DailyWorkingReportData.builder()
        .type(Workingday.WorkingDayType.WORKED)
        .date(LocalDate.of(2024,11,4))
        .startTime(LocalTime.of(9,0))
        .breakDuration(LocalTime.of(0,30))
        .dailyReports(List.of(
            DailyReportData.builder()
                .date("2024-11-04")
                .suborderSign("111/01")
                .hours(0)
                .minutes(30)
                .comment("Team-Mittag")
                .build(),
            DailyReportData.builder()
                .date("2024-11-04")
                .suborderSign("111/01")
                .hours(7)
                .minutes(30)
                .comment("Daily")
                .build()
        ))
        .build();
    }

}