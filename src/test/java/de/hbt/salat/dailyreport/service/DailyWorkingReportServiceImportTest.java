package de.hbt.salat.dailyreport.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
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
import static de.hbt.salat.common.exception.ErrorCode.TR_BOOKING_AMBIGUOUS_EMPLOYEE_ORDER;
import static de.hbt.salat.common.exception.ErrorCode.TR_BOOKING_NO_CONTRACT;
import static de.hbt.salat.common.exception.ErrorCode.TR_BOOKING_NO_EMPLOYEE_ORDER;
import static de.hbt.salat.common.exception.ErrorCode.TR_BOOKING_OF_OTHER_EMPLOYEE;
import static de.hbt.salat.common.exception.ErrorCode.TR_BOOKING_ORDER_CONTRADICTS_SIGN;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpOutputMessage;
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.domain.Workingday.WorkingDayType;
import de.hbt.salat.dailyreport.persistence.TimereportDAO;
import de.hbt.salat.dailyreport.persistence.WorkingdayDAO;
import de.hbt.salat.dailyreport.rest.DailyReportData;
import de.hbt.salat.dailyreport.rest.DailyWorkingReportCsvConverter;
import de.hbt.salat.dailyreport.rest.DailyWorkingReportData;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.persistence.EmployeecontractDAO;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.persistence.EmployeeorderDAO;
import de.hbt.salat.order.service.EmployeeorderService;

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
  private static final long EMPLOYEE_ID = 42L;

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
  private EmployeeService employeeService;
  @Mock
  private EmployeecontractService employeecontractService;
  @Mock
  private AuthorizedUser authorizedUser;
  @Mock
  private AuthorizedEmployee authorizedEmployee;

  private DailyWorkingReportService service;
  private DailyWorkingReportCsvConverter converter;
  private Employee employee;
  private Employeecontract contract;
  private Employeeorder employeeorder;

  @BeforeEach
  void setUp() {
    employee = employee(EMPLOYEE_ID, "testuser");
    contract = contract(CONTRACT_ID, employee);
    employeeorder = employeeorder(ORDER_ID, contract, suborder("111", "Rumsitzen", "01", "Stuhlpolsterung"));

    when(employeeService.getEmployeeBySign("testuser")).thenReturn(employee);
    when(employeecontractDAO.getEmployeecontractById(CONTRACT_ID)).thenReturn(contract);
    when(employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(EMPLOYEE_ID, DAY)).thenReturn(contract);
    when(employeecontractService.getEmployeeContractValidAt(EMPLOYEE_ID, DAY)).thenReturn(contract);
    when(employeeorderDAO.getEmployeeorderById(ORDER_ID)).thenReturn(employeeorder);
    when(employeeorderService.getEmployeeorderById(ORDER_ID)).thenReturn(employeeorder);
    when(employeeorderService.getEmployeeordersByCompleteOrderSignValidAt(CONTRACT_ID, "111/01", DAY)).thenReturn(List.of(employeeorder));

    var resolver = new BookingOrderResolver(employeeService, employeecontractService, employeeorderService, authorizedEmployee);
    service = new DailyWorkingReportService(employeecontractDAO, employeeorderDAO, workingdayDAO, workingdayService,
        timereportService, timereportDAO, resolver);
    converter = new DailyWorkingReportCsvConverter(resolver, authorizedUser);
  }

  @Test
  void a_round_trip_without_changes_creates_and_deletes_nothing() throws IOException {
    stored(booking(1L, "Team-Mittag", 30, "ERP-1"), trainingBooking(2L, "Daily", 450, null));

    var csv = export();
    var report = service.updateReports(read(csv), employee);

    assertThat(csv).startsWith("date,type,startTime,breakTime,employeeorderId,orderSign,orderLabel,suborderSign,suborderLabel,"
        + "workingTime,comment,ticketReference,training,employeeSign\n");
    assertThat(csv).contains(",111/01,Stuhlpolsterung,00:30,Team-Mittag,ERP-1,false,testuser\n", ",07:30,Daily,,true,testuser\n");
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
        """), employee);

    assertNothingCreatedOrDeleted();
  }

  @Test
  void a_file_without_the_column_leaves_both_of_two_otherwise_equal_bookings_their_reference() throws IOException {
    stored(booking(1L, "Daily", 15, "ERP-1"), booking(2L, "Daily", 15, "ERP-2"));

    service.updateReports(read("""
        date,type,startTime,breakTime,employeeorderId,workingTime,comment
        2024-11-04,WORKED,09:00,00:30,183209,00:15,Daily
        2024-11-04,,,,183209,00:15,Daily
        """), employee);

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
        """), employee);

    verify(timereportService).deleteTimereportsById(List.of(1L));
    verify(timereportService).createTimereports(CONTRACT_ID, ORDER_ID, DAY, "Team-Mittag", List.of(), false, 0, 45, 1);
  }

  @Test
  void a_changed_reference_replaces_the_booking_and_shows_as_updated() throws IOException {
    stored(booking(1L, "Team-Mittag", 30, "ERP-1"));

    var report = service.updateReports(read("""
        date,type,startTime,breakTime,employeeorderId,workingTime,comment,ticketReference
        2024-11-04,WORKED,09:00,00:30,183209,00:30,Team-Mittag, ERP-2\s
        """), employee);

    verify(timereportService).deleteTimereportsById(List.of(1L));
    verify(timereportService).createTimereports(CONTRACT_ID, ORDER_ID, DAY, "Team-Mittag", List.of("ERP-2"), false, 0, 30, 1);
    assertThat(report.days()).singleElement().satisfies(day ->
        assertThat(day.bookingsUpdated()).singleElement().satisfies(updated -> {
          assertThat(updated.from().ticketReferences()).containsExactly("ERP-1");
          assertThat(updated.to().ticketReferences()).containsExactly("ERP-2");
        }));
  }

  /* A column that is there but empty is not a missing one: it says "no reference". */
  @Test
  void an_empty_column_removes_the_reference() throws IOException {
    stored(booking(1L, "Team-Mittag", 30, "ERP-1"));

    service.updateReports(read("""
        date,type,startTime,breakTime,employeeorderId,workingTime,comment,ticketReference
        2024-11-04,WORKED,09:00,00:30,183209,00:30,Team-Mittag,
        """), employee);

    verify(timereportService).deleteTimereportsById(List.of(1L));
    verify(timereportService).createTimereports(CONTRACT_ID, ORDER_ID, DAY, "Team-Mittag", List.of(), false, 0, 30, 1);
  }

  @Test
  void adding_a_new_booking_passes_its_reference_on() throws IOException {
    stored();

    service.createReports(read("""
        date,type,startTime,breakTime,employeeorderId,workingTime,comment,ticketReference
        2024-11-04,WORKED,09:00,00:30,183209,00:30,Team-Mittag,ERP-1
        """), employee);

    verify(timereportService).createTimereports(CONTRACT_ID, ORDER_ID, DAY, "Team-Mittag", List.of("ERP-1"), false, 0, 30, 1);
  }

  /* Several references in one cell become the references of the booking, normalized (#1326). */
  @Test
  void imports_several_references_of_a_booking() throws IOException {
    stored();

    service.createReports(read("""
        date,type,startTime,breakTime,employeeorderId,workingTime,comment,ticketReference
        2024-11-04,WORKED,09:00,00:30,183209,00:30,Team-Mittag,erp-1;ERP-2
        """), employee);

    verify(timereportService).createTimereports(CONTRACT_ID, ORDER_ID, DAY, "Team-Mittag", List.of("ERP-1", "ERP-2"),
        false, 0, 30, 1);
  }

  /* A file without the column keeps every reference of a booking that is otherwise the same (#1140, #1326). */
  @Test
  void a_file_without_the_column_keeps_all_references() throws IOException {
    stored(bookingWithReferences(1L, "Team-Mittag", 30, List.of("ERP-1", "ERP-2")));

    service.updateReports(read("""
        date,type,startTime,breakTime,employeeorderId,workingTime,comment
        2024-11-04,WORKED,09:00,00:30,183209,00:30,Team-Mittag
        """), employee);

    verify(timereportService, never()).createTimereports(anyLong(), anyLong(), any(), any(), any(), anyBoolean(), anyLong(),
        anyLong(), anyInt());
    verify(timereportService, never()).deleteTimereportsById(any());
  }

  /* The REST API hands its bookings over without the converter, so the service draws the line too. */
  @Test
  void rejects_a_too_long_reference_before_saving_anything() {
    stored(booking(1L, "Team-Mittag", 30, "ERP-1"));
    var report = DailyWorkingReportData.builder()
        .date(DAY).type(WorkingDayType.WORKED).startTime(LocalTime.of(9, 0)).breakDuration(LocalTime.of(0, 30))
        .dailyReports(List.of(DailyReportData.builder()
            .date("2024-11-04").employeeorderId(ORDER_ID).hours(0).minutes(30).comment("Team-Mittag")
            .ticketReferences(List.of("X".repeat(65)))
            .build()))
        .build();

    assertThatThrownBy(() -> service.updateReports(List.of(report), employee))
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
        DailyReportData.valueOf(booking(1L, "Team-Mittag", 30, null)).withoutId().withTicketReferences(null)));

    verify(timereportService).deleteTimeReports(DAY, ORDER_ID);
    verify(timereportService).createTimereports(CONTRACT_ID, ORDER_ID, DAY, "Team-Mittag", List.of("ERP-1"), false, 0, 30, 1);
  }

  @Test
  void replacing_via_the_api_removes_the_reference_on_an_empty_text() {
    stored(booking(1L, "Team-Mittag", 30, "ERP-1"));

    service.replaceDailyReports(DAY, employeeorder, List.of(
        DailyReportData.valueOf(booking(1L, "Team-Mittag", 30, null)).withoutId().withTicketReferences(null)
            .toBuilder().ticketReference("").build()));

    verify(timereportService).createTimereports(CONTRACT_ID, ORDER_ID, DAY, "Team-Mittag", List.of(), false, 0, 30, 1);
  }

  @Test
  void adding_a_training_booking_passes_the_flag_on() throws IOException {
    stored();

    service.createReports(read("""
        date,type,startTime,breakTime,employeeorderId,workingTime,comment,training
        2024-11-04,WORKED,09:00,00:30,183209,01:00,Schulung,true
        """), employee);

    verify(timereportService).createTimereports(CONTRACT_ID, ORDER_ID, DAY, "Schulung", List.of(), true, 1, 0, 1);
  }

  /* The agreed fallback: without the column a booking is an ordinary one, so replacing turns a
     stored training booking into an ordinary one - and the import report says so. */
  @Test
  void a_file_without_the_training_column_replaces_a_training_booking_with_an_ordinary_one() throws IOException {
    stored(trainingBooking(1L, "Schulung", 60, null));

    var report = service.updateReports(read("""
        date,type,startTime,breakTime,employeeorderId,workingTime,comment
        2024-11-04,WORKED,09:00,00:30,183209,01:00,Schulung
        """), employee);

    verify(timereportService).deleteTimereportsById(List.of(1L));
    verify(timereportService).createTimereports(CONTRACT_ID, ORDER_ID, DAY, "Schulung", List.of(), false, 1, 0, 1);
    assertThat(report.days()).singleElement().satisfies(day ->
        assertThat(day.bookingsUpdated()).singleElement().satisfies(updated -> {
          assertThat(updated.from().training()).isTrue();
          assertThat(updated.to().training()).isFalse();
        }));
  }

  /* The readable way to name an order (#1142): complete suborder sign, the employee of the selected
     contract, and the day. */
  @Test
  void imports_a_booking_named_by_its_signs_only() throws IOException {
    stored();

    service.createReports(read("""
        date,type,startTime,breakTime,suborderSign,workingTime,comment,employeeSign
        2024-11-04,WORKED,09:00,00:30,111/01,00:30,Team-Mittag,testuser
        """), employee);

    verify(timereportService).createTimereports(CONTRACT_ID, ORDER_ID, DAY, "Team-Mittag", List.of(), false, 0, 30, 1);
  }

  /* The order is resolved before the bookings are grouped and compared: a file naming the orders by
     their signs only changes nothing in the mode "replace", where each booking would otherwise count as
     new, be deleted and be created again. */
  @Test
  void a_round_trip_without_the_id_column_creates_and_deletes_nothing() throws IOException {
    stored(booking(1L, "Team-Mittag", 30, "ERP-1"), trainingBooking(2L, "Daily", 450, null));

    var withoutIds = export().lines()
        // the header stays, the id of every booking goes
        .map(line -> line.replaceFirst("^(\\d[^,]*,[^,]*,[^,]*,[^,]*,)[^,]*,", "$1,"))
        .collect(joining("\n", "", "\n"));
    var report = service.updateReports(read(withoutIds), employee);

    assertThat(withoutIds).doesNotContain(String.valueOf(ORDER_ID));
    assertNothingCreatedOrDeleted();
    assertThat(report.totalBookingsCreated() + report.totalBookingsDeleted() + report.totalBookingsUpdated()).isZero();
  }

  /* Variant (a): the id keeps working and takes precedence - but a suborder sign in the same line has to
     agree with it. Copying a line and changing its sign must not book on the order the id names. */
  @Test
  void rejects_a_line_whose_id_contradicts_its_suborder_sign() {
    stored();

    assertThatThrownBy(() -> read("""
        date,type,startTime,breakTime,employeeorderId,suborderSign,workingTime,comment
        2024-11-04,WORKED,09:00,00:30,183209,111/02,00:30,Team-Mittag
        """))
        .isInstanceOfSatisfying(InvalidDataException.class, ex -> assertRejected(ex, 2L, TR_BOOKING_ORDER_CONTRADICTS_SIGN));
    assertNothingCreatedOrDeleted();
  }

  /* A file spanning a change of contract books each day on the contract valid then (#1142). */
  @Test
  void books_each_day_on_the_contract_valid_that_day() throws IOException {
    var nextDay = DAY.plusDays(1);
    var nextContract = contract(8L, employee);
    var nextOrder = employeeorder(183210L, nextContract, employeeorder.getSuborder());
    when(employeecontractDAO.getEmployeecontractById(8L)).thenReturn(nextContract);
    when(employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(EMPLOYEE_ID, nextDay)).thenReturn(nextContract);
    when(employeecontractService.getEmployeeContractValidAt(EMPLOYEE_ID, nextDay)).thenReturn(nextContract);
    when(employeeorderDAO.getEmployeeorderById(183210L)).thenReturn(nextOrder);
    when(employeeorderService.getEmployeeorderById(183210L)).thenReturn(nextOrder);
    when(employeeorderService.getEmployeeordersByCompleteOrderSignValidAt(8L, "111/01", nextDay)).thenReturn(List.of(nextOrder));
    stored();

    service.createReports(read("""
        date,type,startTime,breakTime,suborderSign,workingTime,comment
        2024-11-04,WORKED,09:00,00:30,111/01,00:30,alter Vertrag
        2024-11-05,WORKED,09:00,00:30,111/01,00:45,neuer Vertrag
        """), employee);

    verify(timereportService).createTimereports(CONTRACT_ID, ORDER_ID, DAY, "alter Vertrag", List.of(), false, 0, 30, 1);
    verify(timereportService).createTimereports(8L, 183210L, nextDay, "neuer Vertrag", List.of(), false, 0, 45, 1);
  }

  @Test
  void rejects_a_suborder_sign_without_a_matching_order_with_its_line() {
    stored();

    assertThatThrownBy(() -> read("""
        date,type,startTime,breakTime,suborderSign,workingTime,comment
        2024-11-04,WORKED,09:00,00:30,111/01,00:30,Team-Mittag
        2024-11-04,,,,111/1,00:15,Daily
        """))
        .isInstanceOfSatisfying(InvalidDataException.class, ex -> assertRejected(ex, 3L, TR_BOOKING_NO_EMPLOYEE_ORDER));
    assertNothingCreatedOrDeleted();
  }

  @Test
  void rejects_a_day_without_a_contract_with_its_line() {
    stored();

    assertThatThrownBy(() -> read("""
        date,type,startTime,breakTime,suborderSign,workingTime,comment
        2024-11-06,WORKED,09:00,00:30,111/01,00:30,Team-Mittag
        """))
        .isInstanceOfSatisfying(InvalidDataException.class, ex -> assertRejected(ex, 2L, TR_BOOKING_NO_CONTRACT));
  }

  /* Several matching orders are not resolved silently. */
  @Test
  void rejects_a_suborder_sign_matching_several_orders() {
    var second = employeeorder(183211L, contract, employeeorder.getSuborder());
    when(employeeorderService.getEmployeeordersByCompleteOrderSignValidAt(CONTRACT_ID, "111/01", DAY))
        .thenReturn(List.of(employeeorder, second));

    assertThatThrownBy(() -> read("""
        date,type,startTime,breakTime,suborderSign,workingTime,comment
        2024-11-04,WORKED,09:00,00:30,111/01,00:30,Team-Mittag
        """))
        .isInstanceOfSatisfying(InvalidDataException.class, ex -> assertRejected(ex, 2L, TR_BOOKING_AMBIGUOUS_EMPLOYEE_ORDER));
  }

  /* The file belongs to the employee of the selected contract, not to whoever is logged in. */
  @Test
  void imports_for_the_employee_of_the_selected_contract_rather_than_the_one_logged_in() throws IOException {
    when(authorizedEmployee.getSign()).thenReturn("chef");
    when(employeeService.getEmployeeBySign("chef")).thenReturn(employee(43L, "chef"));
    stored();

    service.createReports(read("""
        date,type,startTime,breakTime,suborderSign,workingTime,comment
        2024-11-04,WORKED,09:00,00:30,111/01,00:30,Team-Mittag
        """), employee);

    verify(timereportService).createTimereports(CONTRACT_ID, ORDER_ID, DAY, "Team-Mittag", List.of(), false, 0, 30, 1);
  }

  @Test
  void rejects_an_employee_sign_that_does_not_belong_to_the_selected_contract() {
    when(employeeService.getEmployeeBySign("chef")).thenReturn(employee(43L, "chef"));

    assertThatThrownBy(() -> read("""
        date,type,startTime,breakTime,suborderSign,workingTime,comment,employeeSign
        2024-11-04,WORKED,09:00,00:30,111/01,00:30,Team-Mittag,chef
        """))
        .isInstanceOfSatisfying(InvalidDataException.class, ex -> assertRejected(ex, 2L, TR_BOOKING_OF_OTHER_EMPLOYEE));
  }

  /* An id is no way around the selected contract either. */
  @Test
  void rejects_an_id_whose_order_belongs_to_another_employee() {
    var other = employeeorder(999L, contract(9L, employee(43L, "chef")), employeeorder.getSuborder());
    when(employeeorderService.getEmployeeorderById(999L)).thenReturn(other);

    assertThatThrownBy(() -> read("""
        date,type,startTime,breakTime,employeeorderId,workingTime,comment
        2024-11-04,WORKED,09:00,00:30,999,00:30,Team-Mittag
        """))
        .isInstanceOfSatisfying(InvalidDataException.class, ex -> assertRejected(ex, 2L, TR_BOOKING_OF_OTHER_EMPLOYEE));
  }

  /* The REST API keeps its rule: the id wins, and signs that do not fit it are not evaluated (#1142). */
  @Test
  void the_api_books_on_the_id_even_where_its_signs_do_not_fit() {
    stored();
    var report = DailyWorkingReportData.builder()
        .date(DAY).type(WorkingDayType.WORKED).startTime(LocalTime.of(9, 0)).breakDuration(LocalTime.of(0, 30))
        .dailyReports(List.of(DailyReportData.builder()
            .date("2024-11-04").employeeorderId(ORDER_ID).suborderSign("999/99").employeeSign("niemand")
            .hours(0).minutes(30).comment("Team-Mittag")
            .build()))
        .build();

    service.createReports(List.of(report));

    verify(timereportService).createTimereports(CONTRACT_ID, ORDER_ID, DAY, "Team-Mittag", List.of(), false, 0, 30, 1);
  }

  /* Without an id the API names the order by its signs; a missing employee sign is the one logged in. */
  @Test
  void the_api_books_a_booking_named_by_its_signs() {
    when(authorizedEmployee.getSign()).thenReturn("testuser");
    stored(booking(1L, "Team-Mittag", 30, null));
    var report = DailyWorkingReportData.builder()
        .date(DAY).type(WorkingDayType.WORKED).startTime(LocalTime.of(9, 0)).breakDuration(LocalTime.of(0, 30))
        .dailyReports(List.of(DailyReportData.builder()
            .suborderSign("111/01").hours(0).minutes(30).comment("Team-Mittag")
            .build()))
        .build();

    service.updateReports(List.of(report));

    assertNothingCreatedOrDeleted();
  }

  @Test
  void the_api_rejects_a_suborder_sign_without_a_matching_order() {
    var report = DailyWorkingReportData.builder()
        .date(DAY).type(WorkingDayType.WORKED).startTime(LocalTime.of(9, 0)).breakDuration(LocalTime.of(0, 30))
        .dailyReports(List.of(DailyReportData.builder()
            .suborderSign("111/1").employeeSign("testuser").hours(0).minutes(30).comment("Team-Mittag")
            .build()))
        .build();

    assertThatThrownBy(() -> service.createReports(List.of(report)))
        .isInstanceOfSatisfying(InvalidDataException.class, ex ->
            assertThat(ex.getMessages().getFirst().getErrorCode()).isEqualTo(TR_BOOKING_NO_EMPLOYEE_ORDER));
    assertNothingCreatedOrDeleted();
  }

  // fixtures

  private static void assertRejected(InvalidDataException ex, long line, ErrorCode reason) {
    assertThat(ex.getMessages()).singleElement().satisfies(message -> {
      assertThat(message.getErrorCode()).isEqualTo(ErrorCode.TR_CSV_LINE_REJECTED);
      assertThat(message.getArguments().getFirst()).isEqualTo(line);
      assertThat(message.getArguments().get(1)).isInstanceOfSatisfying(ServiceFeedbackMessage.class, nested ->
          assertThat(nested.getErrorCode()).isEqualTo(reason));
    });
  }

  private static Employee employee(long id, String sign) {
    var employee = new Employee();
    ReflectionTestUtils.setField(employee, "id", id);
    employee.setSign(sign);
    return employee;
  }

  private static Employeecontract contract(long id, Employee employee) {
    var contract = new Employeecontract();
    ReflectionTestUtils.setField(contract, "id", id);
    contract.setEmployee(employee);
    return contract;
  }

  private static Suborder suborder(String customerorderSign, String customerorderDescription, String sign, String description) {
    var customerorder = new Customerorder();
    customerorder.setSign(customerorderSign);
    customerorder.setDescription(customerorderDescription);
    var suborder = new Suborder();
    suborder.setSign(sign);
    suborder.setDescription(description);
    suborder.setCustomerorder(customerorder);
    return suborder;
  }

  private static Employeeorder employeeorder(long id, Employeecontract contract, Suborder suborder) {
    var employeeorder = new Employeeorder();
    ReflectionTestUtils.setField(employeeorder, "id", id);
    employeeorder.setSuborder(suborder);
    employeeorder.setEmployeecontract(contract);
    return employeeorder;
  }


  private List<DailyWorkingReportData> read(String csv) throws IOException {
    return converter.read(new ByteArrayInputStream(csv.getBytes(UTF_8)), employee).reports();
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

  private static TimereportDTO bookingWithReferences(long id, String comment, int minutes, List<String> ticketReferences) {
    return booking(id, comment, minutes, ticketReferences, false);
  }

  private static TimereportDTO booking(long id, String comment, int minutes, String ticketReference, boolean training) {
    return booking(id, comment, minutes, ticketReference == null ? List.of() : List.of(ticketReference), training);
  }

  private static TimereportDTO booking(long id, String comment, int minutes, List<String> ticketReferences, boolean training) {
    return TimereportDTO.builder()
        .id(id)
        .referenceday(DAY)
        .employeeorderId(ORDER_ID)
        .employeecontractId(CONTRACT_ID)
        .employeeSign("testuser")
        .customerorderSign("111")
        .customerorderDescription("Rumsitzen")
        .completeOrderSign("111/01")
        .suborderDescription("Stuhlpolsterung")
        .duration(Duration.ofMinutes(minutes))
        .taskdescription(comment)
        .ticketReferences(ticketReferences)
        .training(training)
        .build();
  }

  private void assertNothingCreatedOrDeleted() {
    verify(timereportService, never()).deleteTimereportsById(any());
    verify(timereportService, never()).createTimereports(anyLong(), anyLong(), any(), any(), any(), anyBoolean(), anyLong(),
        anyLong(), anyInt());
  }
}
