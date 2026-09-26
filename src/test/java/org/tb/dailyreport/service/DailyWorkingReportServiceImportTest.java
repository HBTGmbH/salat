package org.tb.dailyreport.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpOutputMessage;
import org.springframework.test.util.ReflectionTestUtils;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.InvalidDataException;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.Workingday.WorkingDayType;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.dailyreport.rest.DailyReportData;
import org.tb.dailyreport.rest.DailyWorkingReportCsvConverter;
import org.tb.dailyreport.rest.DailyWorkingReportData;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.EmployeeorderDAO;
import org.tb.order.service.EmployeeorderService;

/**
 * Ticket reference and training flag in the import and in {@code PUT /list} (#1140). Import and export run
 * through the real CSV converter, so that "the file has no such column" and "the column is empty"
 * are what the converter actually produces, not what a test assumes it does.
 *
 * <p>The import compares incoming and stored bookings by {@link DailyReportData#equals}; once the
 * reference is part of it, a file without the column must not make every booking with a reference
 * look changed — in the mode "replace" that would delete it and create it again without one.
 *
 * <p>The training flag has no such carry-over: a missing column means {@code false}, as a missing
 * field does in the REST API.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class DailyWorkingReportServiceImportTest {

  private static final LocalDate DAY = LocalDate.of(2024, 11, 4);
  private static final long CONTRACT_ID = 7L;
  private static final long ORDER_ID = 183209L;

  @Mock
  private EmployeecontractDAO employeecontractDAO;
  @Mock
  private EmployeeorderDAO employeeorderDAO;
  @Mock
  private WorkingdayDAO workingdayDAO;
  @Mock
  private WorkingdayService workingdayService;
  @Mock
  private TimereportService timereportService;
  @Mock
  private TimereportDAO timereportDAO;

  @Mock
  private EmployeeorderService employeeorderService;
  @Mock
  private AuthorizedUser authorizedUser;
  @Mock
  private AuthorizedEmployee authorizedEmployee;

  @InjectMocks
  private DailyWorkingReportService service;

  private DailyWorkingReportCsvConverter converter;
  private Employeeorder employeeorder;

  @BeforeEach
  void setUp() {
    var contract = new Employeecontract();
    ReflectionTestUtils.setField(contract, "id", CONTRACT_ID);
    var customerorder = new Customerorder();
    customerorder.setSign("111");
    customerorder.setDescription("Rumsitzen");
    var suborder = new Suborder();
    suborder.setSign("01");
    suborder.setDescription("Stuhlpolsterung");
    suborder.setCustomerorder(customerorder);
    employeeorder = new Employeeorder();
    ReflectionTestUtils.setField(employeeorder, "id", ORDER_ID);
    employeeorder.setSuborder(suborder);
    employeeorder.setEmployeecontract(contract);

    when(employeecontractDAO.getEmployeecontractById(CONTRACT_ID)).thenReturn(contract);
    when(employeeorderDAO.getEmployeeorderById(ORDER_ID)).thenReturn(employeeorder);
    when(employeeorderService.getEmployeeorderById(ORDER_ID)).thenReturn(employeeorder);
    converter = new DailyWorkingReportCsvConverter(employeeorderService, authorizedUser, authorizedEmployee);
  }

  @Test
  void a_round_trip_without_changes_creates_and_deletes_nothing() throws IOException {
    stored(booking(1L, "Team-Mittag", 30, "ERP-1"), trainingBooking(2L, "Daily", 450, null));

    var csv = export();
    var report = service.updateReports(converter.read(new ByteArrayInputStream(csv.getBytes(UTF_8))).reports(), CONTRACT_ID);

    assertThat(csv).startsWith("date,type,startTime,breakTime,employeeorderId,orderSign,orderLabel,suborderSign,suborderLabel,"
        + "workingTime,comment,ticketReference,training\n");
    assertThat(csv).contains(",00:30,Team-Mittag,ERP-1,false\n", ",07:30,Daily,,true\n");
    assertNothingCreatedOrDeleted();
    assertThat(report.totalBookingsCreated() + report.totalBookingsDeleted() + report.totalBookingsUpdated()).isZero();
  }

  @Test
  void a_file_without_the_column_keeps_the_stored_references() throws IOException {
    stored(booking(1L, "Team-Mittag", 30, "ERP-1"), booking(2L, "Daily", 450, "ERP-2"));

    service.updateReports(read("""
        date,type,startTime,breakTime,employeeorderId,workingTime,comment
        2024-11-04,WORKED,09:00,00:30,183209,00:30,Team-Mittag
        2024-11-04,,,,183209,07:30,Daily
        """), CONTRACT_ID);

    assertNothingCreatedOrDeleted();
  }

  @Test
  void a_file_without_the_column_leaves_both_of_two_otherwise_equal_bookings_their_reference() throws IOException {
    stored(booking(1L, "Daily", 15, "ERP-1"), booking(2L, "Daily", 15, "ERP-2"));

    service.updateReports(read("""
        date,type,startTime,breakTime,employeeorderId,workingTime,comment
        2024-11-04,WORKED,09:00,00:30,183209,00:15,Daily
        2024-11-04,,,,183209,00:15,Daily
        """), CONTRACT_ID);

    assertNothingCreatedOrDeleted();
  }

  /* Which stored booking a changed one replaces cannot be told once there are several per order and
     day. A file without the column therefore creates the changed booking without a reference. */
  @Test
  void a_file_without_the_column_creates_a_changed_booking_without_a_reference() throws IOException {
    stored(booking(1L, "Team-Mittag", 30, "ERP-1"));

    service.updateReports(read("""
        date,type,startTime,breakTime,employeeorderId,workingTime,comment
        2024-11-04,WORKED,09:00,00:30,183209,00:45,Team-Mittag
        """), CONTRACT_ID);

    verify(timereportService).deleteTimereportsById(List.of(1L));
    verify(timereportService).createTimereports(CONTRACT_ID, ORDER_ID, DAY, "Team-Mittag", null, false, 0, 45, 1);
  }

  @Test
  void a_changed_reference_replaces_the_booking_and_shows_as_updated() throws IOException {
    stored(booking(1L, "Team-Mittag", 30, "ERP-1"));

    var report = service.updateReports(read("""
        date,type,startTime,breakTime,employeeorderId,workingTime,comment,ticketReference
        2024-11-04,WORKED,09:00,00:30,183209,00:30,Team-Mittag, ERP-2\s
        """), CONTRACT_ID);

    verify(timereportService).deleteTimereportsById(List.of(1L));
    verify(timereportService).createTimereports(CONTRACT_ID, ORDER_ID, DAY, "Team-Mittag", "ERP-2", false, 0, 30, 1);
    assertThat(report.days()).singleElement().satisfies(day ->
        assertThat(day.bookingsUpdated()).singleElement().satisfies(updated -> {
          assertThat(updated.from().ticketReference()).isEqualTo("ERP-1");
          assertThat(updated.to().ticketReference()).isEqualTo("ERP-2");
        }));
  }

  /* A column that is there but empty is not a missing one: it says "no reference". */
  @Test
  void an_empty_column_removes_the_reference() throws IOException {
    stored(booking(1L, "Team-Mittag", 30, "ERP-1"));

    service.updateReports(read("""
        date,type,startTime,breakTime,employeeorderId,workingTime,comment,ticketReference
        2024-11-04,WORKED,09:00,00:30,183209,00:30,Team-Mittag,
        """), CONTRACT_ID);

    verify(timereportService).deleteTimereportsById(List.of(1L));
    verify(timereportService).createTimereports(CONTRACT_ID, ORDER_ID, DAY, "Team-Mittag", null, false, 0, 30, 1);
  }

  @Test
  void adding_a_new_booking_passes_its_reference_on() throws IOException {
    stored();

    service.createReports(read("""
        date,type,startTime,breakTime,employeeorderId,workingTime,comment,ticketReference
        2024-11-04,WORKED,09:00,00:30,183209,00:30,Team-Mittag,ERP-1
        """), CONTRACT_ID);

    verify(timereportService).createTimereports(CONTRACT_ID, ORDER_ID, DAY, "Team-Mittag", "ERP-1", false, 0, 30, 1);
  }

  /* The REST API hands its bookings over without the converter, so the service draws the line too. */
  @Test
  void rejects_a_too_long_reference_before_saving_anything() {
    stored(booking(1L, "Team-Mittag", 30, "ERP-1"));
    var report = DailyWorkingReportData.builder()
        .date(DAY).type(WorkingDayType.WORKED).startTime(LocalTime.of(9, 0)).breakDuration(LocalTime.of(0, 30))
        .dailyReports(List.of(DailyReportData.builder()
            .date("2024-11-04").employeeorderId(ORDER_ID).hours(0).minutes(30).comment("Team-Mittag")
            .ticketReference("X".repeat(65))
            .build()))
        .build();

    assertThatThrownBy(() -> service.updateReports(List.of(report), CONTRACT_ID))
        .isInstanceOfSatisfying(InvalidDataException.class, ex ->
            assertThat(ex.getMessages().getFirst().getErrorCode()).isEqualTo(ErrorCode.TR_TICKET_REFERENCE_INVALID_LENGTH));
    assertNothingCreatedOrDeleted();
  }

  /* PUT /list replaces the bookings of the day and order; a client that does not know the field
     must not delete the references it never saw. */
  @Test
  void replacing_via_the_api_keeps_the_reference_of_a_booking_without_the_field() {
    stored(booking(1L, "Team-Mittag", 30, "ERP-1"));

    service.replaceDailyReports(DAY, employeeorder, List.of(
        DailyReportData.valueOf(booking(1L, "Team-Mittag", 30, null)).withoutId()));

    verify(timereportService).deleteTimeReports(DAY, ORDER_ID);
    verify(timereportService).createTimereports(CONTRACT_ID, ORDER_ID, DAY, "Team-Mittag", "ERP-1", false, 0, 30, 1);
  }

  @Test
  void replacing_via_the_api_removes_the_reference_on_an_empty_text() {
    stored(booking(1L, "Team-Mittag", 30, "ERP-1"));

    service.replaceDailyReports(DAY, employeeorder, List.of(
        DailyReportData.valueOf(booking(1L, "Team-Mittag", 30, null)).withoutId().withTicketReference("")));

    verify(timereportService).createTimereports(CONTRACT_ID, ORDER_ID, DAY, "Team-Mittag", null, false, 0, 30, 1);
  }

  @Test
  void adding_a_training_booking_passes_the_flag_on() throws IOException {
    stored();

    service.createReports(read("""
        date,type,startTime,breakTime,employeeorderId,workingTime,comment,training
        2024-11-04,WORKED,09:00,00:30,183209,01:00,Schulung,true
        """), CONTRACT_ID);

    verify(timereportService).createTimereports(CONTRACT_ID, ORDER_ID, DAY, "Schulung", null, true, 1, 0, 1);
  }

  /* The agreed fallback: without the column a booking is an ordinary one, so replacing turns a
     stored training booking into an ordinary one - and the import report says so. */
  @Test
  void a_file_without_the_training_column_replaces_a_training_booking_with_an_ordinary_one() throws IOException {
    stored(trainingBooking(1L, "Schulung", 60, null));

    var report = service.updateReports(read("""
        date,type,startTime,breakTime,employeeorderId,workingTime,comment
        2024-11-04,WORKED,09:00,00:30,183209,01:00,Schulung
        """), CONTRACT_ID);

    verify(timereportService).deleteTimereportsById(List.of(1L));
    verify(timereportService).createTimereports(CONTRACT_ID, ORDER_ID, DAY, "Schulung", null, false, 1, 0, 1);
    assertThat(report.days()).singleElement().satisfies(day ->
        assertThat(day.bookingsUpdated()).singleElement().satisfies(updated -> {
          assertThat(updated.from().training()).isTrue();
          assertThat(updated.to().training()).isFalse();
        }));
  }

  // fixtures


  private List<DailyWorkingReportData> read(String csv) throws IOException {
    return converter.read(new ByteArrayInputStream(csv.getBytes(UTF_8))).reports();
  }

  private String export() throws IOException {
    var stored = timereportDAO.getTimereportsByDateAndEmployeeOrderId(DAY, ORDER_ID).stream()
        .map(DailyReportData::valueOf).toList();
    var day = DailyWorkingReportData.builder()
        .date(DAY).type(WorkingDayType.WORKED).startTime(LocalTime.of(9, 0)).breakDuration(LocalTime.of(0, 30))
        .dailyReports(stored)
        .build();
    var out = new ByteArrayOutputStream();
    converter.write(List.of(day), null, new HttpOutputMessage() {
      @Override public OutputStream getBody() { return out; }
      @Override public HttpHeaders getHeaders() { return new HttpHeaders(); }
    });
    return out.toString(UTF_8);
  }

  private void stored(TimereportDTO... bookings) {
    when(timereportDAO.getTimereportsByDateAndEmployeeOrderId(DAY, ORDER_ID)).thenReturn(Arrays.asList(bookings));
  }

  private static TimereportDTO booking(long id, String comment, int minutes, String ticketReference) {
    return booking(id, comment, minutes, ticketReference, false);
  }

  private static TimereportDTO trainingBooking(long id, String comment, int minutes, String ticketReference) {
    return booking(id, comment, minutes, ticketReference, true);
  }

  private static TimereportDTO booking(long id, String comment, int minutes, String ticketReference, boolean training) {
    return TimereportDTO.builder()
        .id(id)
        .referenceday(DAY)
        .employeeorderId(ORDER_ID)
        .employeecontractId(CONTRACT_ID)
        .customerorderSign("111")
        .customerorderDescription("Rumsitzen")
        .completeOrderSign("111/01")
        .suborderDescription("Stuhlpolsterung")
        .duration(Duration.ofMinutes(minutes))
        .taskdescription(comment)
        .ticketReference(ticketReference)
        .training(training)
        .build();
  }

  private void assertNothingCreatedOrDeleted() {
    verify(timereportService, never()).deleteTimereportsById(any());
    verify(timereportService, never()).createTimereports(anyLong(), anyLong(), any(), any(), any(), anyBoolean(), anyLong(),
        anyLong(), anyInt());
  }
}
