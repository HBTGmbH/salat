package de.hbt.salat.dailyreport.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static de.hbt.salat.common.exception.ErrorCode.TR_BOOKING_NO_EMPLOYEE_ORDER;
import static de.hbt.salat.common.exception.ErrorCode.TR_OPEN_TIME_REPORT_REQ_EMPLOYEE;
import static de.hbt.salat.common.exception.ErrorCode.TR_TICKET_REFERENCE_INVALID_LENGTH;
import static de.hbt.salat.dailyreport.rest.DailyReportData.valueOf;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.service.BookingOrderResolver;
import de.hbt.salat.dailyreport.service.DailyWorkingReportService;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.service.EmployeeorderService;

@ExtendWith(MockitoExtension.class)
class DailyReportRestEndpointTest {
    @Mock
    EmployeeorderService employeeorderService;

    @Mock
    EmployeecontractService employeecontractService;

    @Mock
    AuthorizedEmployee authorizedEmployee;

    @Mock
    TimereportService timereportService;

    @Mock
    DailyWorkingReportService dailyWorkingReportService;

    @Mock
    AuthorizedUser authorizedUser;

    @Mock
    BookingOrderResolver bookingOrderResolver;

    @Captor
    ArgumentCaptor<LocalDate> dateArgumentCaptor;

    @InjectMocks
    DailyReportRestEndpoint dailyReportRestEndpoint;

    @Test
    void shouldGetBookingsUnauthorized() {
        // given
        var day = DateUtils.parse("2024-07-06");

        when(authorizedUser.isAuthenticated()).thenReturn(false);

        // when
        assertThatThrownBy(() -> dailyReportRestEndpoint.getBookings(day, 1, false))
                .hasMessage("401 UNAUTHORIZED");
    }

    @Test
    void shouldGetBookings() {
        // given
        var day = DateUtils.parse("2024-07-06");
        var timeReport1 = TimereportDTO.builder()
                .duration(Duration.ofHours(1))
                .build();
        var timeReport2 = TimereportDTO.builder()
                .duration(Duration.ofHours(2))
                .build();
        var employee = employee();
        var employeeContract = employeeContract(employee);

        when(authorizedEmployee.getEmployeeId()).thenReturn(employee.getId());
        when(authorizedUser.isAuthenticated()).thenReturn(true);
        when(employeecontractService.getEmployeeContractValidAt(employee.getId(), day))
                .thenReturn(employeeContract);
        when(timereportService.getTimereportsByDateAndEmployeeContractId(employeeContract.getId(), day))
                .thenReturn(List.of(timeReport1));
        when(timereportService.getTimereportsByDateAndEmployeeContractId(employeeContract.getId(), day.plusDays(1)))
                .thenReturn(List.of(timeReport2));

        // when
        var result = dailyReportRestEndpoint.getBookings(day, 2, false);

        // then
        assertThat(result.getBody())
                .hasSize(2)
                .contains(valueOf(timeReport1))
                .contains(valueOf(timeReport2));
    }

    @Test
    void shouldGetBookingsForToday() {
        // given
        var today = DateUtils.today();
        var employee = employee();
        var employeeContract = employeeContract(employee);

        when(authorizedEmployee.getEmployeeId()).thenReturn(employee.getId());
        when(authorizedUser.isAuthenticated()).thenReturn(true);
        when(employeecontractService.getEmployeeContractValidAt(eq(employee.getId()), dateArgumentCaptor.capture()))
                .thenReturn(employeeContract);
        when(timereportService.getTimereportsByDateAndEmployeeContractId(eq(employeeContract.getId()),  dateArgumentCaptor.capture()))
                .thenReturn(List.of());

        // when
        var result = dailyReportRestEndpoint.getBookings(null, 1, false);

        // then
        assertThat(result.getBody()).isEmpty();
        assertThat(dateArgumentCaptor.getAllValues()).hasSize(2).allMatch(today::equals);
    }

    @Test
    void shouldCreateBookingUnauthorized() {
        // given
        when(authorizedUser.isAuthenticated()).thenReturn(false);

        // when
        assertThatThrownBy(() -> dailyReportRestEndpoint.createBooking(DailyReportData.builder().build()))
                .hasMessage("401 UNAUTHORIZED");
    }

    @Test
    void shouldCreateBooking() {
        // given
        var day = DateUtils.parse("2024-07-06");
        var employee = employee();
        var employeeContract = employeeContract(employee);
        var employeeOrder = employeeOrder(employeeContract);
        var timeReport1 = TimereportDTO.builder()
                .employeeorderId(1L).referenceday(day)
                .taskdescription("test").duration(Duration.ofHours(1))
                .build();

        when(authorizedUser.isAuthenticated()).thenReturn(true);
        when(employeeorderService.getEmployeeorderById(1L))
                .thenReturn(employeeOrder);

        // when
        dailyReportRestEndpoint.createBooking(valueOf(timeReport1));

        // then
        verify(timereportService, times(1)).createTimereports(
                employeeContract.getId(), employeeOrder.getId(), day, "test",
                List.of(), false,1, 0, 1);
    }

    @Test
    void shouldCreateBookingsUnauthorized() {
        // given
        when(authorizedUser.isAuthenticated()).thenReturn(false);

        // when
        assertThatThrownBy(() -> dailyReportRestEndpoint.createBookings(List.of()))
                .hasMessage("401 UNAUTHORIZED");
    }

    @Test
    void shouldCreateBookings() {
        // given
        var day = DateUtils.parse("2024-07-06");
        var employee = employee();
        var employeeContract = employeeContract(employee);
        var employeeOrder = employeeOrder(employeeContract);
        var timeReport1 = TimereportDTO.builder()
                .employeeorderId(1L).referenceday(day)
                .taskdescription("test").duration(Duration.ofHours(1))
                .build();

        when(authorizedUser.isAuthenticated()).thenReturn(true);
        when(employeeorderService.getEmployeeorderById(1L))
                .thenReturn(employeeOrder);

        // when
        dailyReportRestEndpoint.createBookings(List.of(valueOf(timeReport1)));

        // then
        verify(timereportService, times(1)).createTimereports(
                employeeContract.getId(), employeeOrder.getId(), day, "test",
                List.of(), false,1, 0, 1);
    }

    @Test
    void shouldUpdateBookingsUnauthorized() {
        // given
         when(authorizedUser.isAuthenticated()).thenReturn(false);

        // when
        assertThatThrownBy(() -> dailyReportRestEndpoint.updateBookings(List.of()))
                .hasMessage("401 UNAUTHORIZED");
    }

    @Test
    void shouldUpdateBookings() {
        // given
        var day = DateUtils.parse("2024-07-06");
        var employee = employee();
        var employeeContract = employeeContract(employee);
        var employeeOrder1 = employeeOrder(employeeContract);
        var timeReport1 = TimereportDTO.builder()
                .employeeorderId(1L).referenceday(day)
                .taskdescription("test1").duration(Duration.ofHours(1))
                .build();
        var employeeOrder2 = employeeOrder(employeeContract);
        var timeReport2 = TimereportDTO.builder()
                .employeeorderId(2L).referenceday(day)
                .taskdescription("test2").duration(Duration.ofHours(1))
                .build();
        var timeReport3 = TimereportDTO.builder()
                .employeeorderId(2L).referenceday(day)
                .taskdescription("test3").duration(Duration.ofHours(1))
                .build();

        when(authorizedUser.isAuthenticated()).thenReturn(true);
        when(employeeorderService.getEmployeeorderById(1L))
                .thenReturn(employeeOrder1);
        when(employeeorderService.getEmployeeorderById(2L))
                .thenReturn(employeeOrder2);

        // when
        dailyReportRestEndpoint.updateBookings(List.of(
                valueOf(timeReport1),
                valueOf(timeReport2),
                valueOf(timeReport3)
        ));

        // then
        verify(dailyWorkingReportService, times(1)).replaceDailyReports(day, employeeOrder1, List.of(valueOf(timeReport1)));
        verify(dailyWorkingReportService, times(1)).replaceDailyReports(day, employeeOrder2,
                List.of(valueOf(timeReport2), valueOf(timeReport3)));
    }

    @Test
    void shouldCreateBookingWithTicketReference() {
        // given
        var day = DateUtils.parse("2024-07-06");
        var employeeOrder = employeeOrder(employeeContract(employee()));
        var booking = valueOf(TimereportDTO.builder()
                .employeeorderId(1L).referenceday(day)
                .taskdescription("test").duration(Duration.ofHours(1))
                .ticketReferences(List.of("ERP-1"))
                .build());

        when(authorizedUser.isAuthenticated()).thenReturn(true);
        when(employeeorderService.getEmployeeorderById(1L)).thenReturn(employeeOrder);

        // when
        dailyReportRestEndpoint.createBooking(booking);

        // then
        verify(timereportService, times(1)).createTimereports(
                employeeOrder.getEmployeecontract().getId(), employeeOrder.getId(), day, "test",
                List.of("ERP-1"), false, 1, 0, 1);
    }

    /* The length check sits in the service; over the API its rejection is the caller's error (#1140). */
    @Test
    void shouldAnswerATooLongTicketReferenceWithBadRequest() {
        // given
        var day = DateUtils.parse("2024-07-06");
        var employeeOrder = employeeOrder(employeeContract(employee()));
        var booking = valueOf(TimereportDTO.builder()
                .employeeorderId(1L).referenceday(day)
                .taskdescription("test").duration(Duration.ofHours(1))
                .ticketReferences(List.of("X".repeat(65)))
                .build());

        when(authorizedUser.isAuthenticated()).thenReturn(true);
        when(employeeorderService.getEmployeeorderById(1L)).thenReturn(employeeOrder);
        doThrow(new InvalidDataException(TR_TICKET_REFERENCE_INVALID_LENGTH)).when(timereportService).createTimereports(
                anyLong(), anyLong(), any(), any(), any(), anyBoolean(), anyLong(), anyLong(), anyInt());

        // when
        assertThatThrownBy(() -> dailyReportRestEndpoint.createBooking(booking))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(BAD_REQUEST));
    }

    /* Without an id, complete suborder sign and employee sign name the order (#1142). */
    @Test
    void shouldCreateBookingNamedBySigns() {
        // given
        var day = DateUtils.parse("2024-07-06");
        var employeeOrder = employeeOrder(employeeContract(employee()));
        var booking = DailyReportData.builder()
                .date("2024-07-06").suborderSign("4711/01").employeeSign("abc")
                .hours(1).minutes(0).comment("test")
                .build();

        when(authorizedUser.isAuthenticated()).thenReturn(true);
        when(bookingOrderResolver.resolve(booking, day)).thenReturn(employeeOrder);

        // when
        dailyReportRestEndpoint.createBooking(booking);

        // then
        verify(timereportService).createTimereports(
                employeeOrder.getEmployeecontract().getId(), employeeOrder.getId(), day, "test",
                null, false, 1, 0, 1);
    }

    /* A client that sends the id together with signs of an older read keeps working: the id wins, and the
       signs are not evaluated at all (#1142). */
    @Test
    void shouldBookOnTheIdEvenWhereTheSignsDoNotFit() {
        // given
        var day = DateUtils.parse("2024-07-06");
        var employeeOrder = employeeOrder(employeeContract(employee()));
        var booking = DailyReportData.builder()
                .date("2024-07-06").employeeorderId(1L).orderSign("999").suborderSign("999/99").employeeSign("niemand")
                .hours(1).minutes(0).comment("test")
                .build();

        when(authorizedUser.isAuthenticated()).thenReturn(true);
        when(employeeorderService.getEmployeeorderById(1L)).thenReturn(employeeOrder);

        // when
        dailyReportRestEndpoint.createBooking(booking);

        // then
        verify(timereportService).createTimereports(
                employeeOrder.getEmployeecontract().getId(), employeeOrder.getId(), day, "test",
                null, false, 1, 0, 1);
        verifyNoInteractions(bookingOrderResolver);
    }

    /* No order, no contract or several orders are the caller's error: 400, not a NullPointerException. */
    @Test
    void shouldAnswerSignsWithoutAMatchingOrderWithBadRequest() {
        // given
        var day = DateUtils.parse("2024-07-06");
        var booking = DailyReportData.builder()
                .date("2024-07-06").suborderSign("4711/1").employeeSign("abc").hours(1).minutes(0)
                .build();

        when(authorizedUser.isAuthenticated()).thenReturn(true);
        when(bookingOrderResolver.resolve(booking, day))
                .thenThrow(new InvalidDataException(TR_BOOKING_NO_EMPLOYEE_ORDER, "4711/1", "abc", "2024-07-06"));

        // when / then
        assertThatThrownBy(() -> dailyReportRestEndpoint.createBooking(booking))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(BAD_REQUEST));
        verifyNoInteractions(timereportService);
    }

    /* Naming the order by signs opens nothing: the booking goes through the same service, and the same guard,
       as one named by its id (#1142). */
    @Test
    void shouldAnswerABookingBySignsForSomeoneNotPermittedWithForbidden() {
        // given
        var day = DateUtils.parse("2024-07-06");
        var employeeOrder = employeeOrder(employeeContract(employee()));
        var booking = DailyReportData.builder()
                .date("2024-07-06").suborderSign("4711/01").employeeSign("fremd").hours(1).minutes(0).comment("test")
                .build();

        when(authorizedUser.isAuthenticated()).thenReturn(true);
        when(bookingOrderResolver.resolve(booking, day)).thenReturn(employeeOrder);
        doThrow(new AuthorizationException(TR_OPEN_TIME_REPORT_REQ_EMPLOYEE)).when(timereportService).createTimereports(
                anyLong(), anyLong(), any(), any(), any(), anyBoolean(), anyLong(), anyLong(), anyInt());

        // when / then
        assertThatThrownBy(() -> dailyReportRestEndpoint.createBooking(booking))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(FORBIDDEN));
    }

    /* Replacing groups by the order the bookings name, whether by id or by signs (#1142). */
    @Test
    void shouldUpdateBookingsNamedByIdAndBySignsTogether() {
        // given
        var day = DateUtils.parse("2024-07-06");
        var employeeOrder = employeeOrder(employeeContract(employee()));
        var byId = DailyReportData.builder().date("2024-07-06").employeeorderId(employeeOrder.getId())
                .hours(1).minutes(0).comment("test1").build();
        var bySigns = DailyReportData.builder().date("2024-07-06").suborderSign("4711/01").employeeSign("abc")
                .hours(2).minutes(0).comment("test2").build();

        when(authorizedUser.isAuthenticated()).thenReturn(true);
        when(employeeorderService.getEmployeeorderById(employeeOrder.getId())).thenReturn(employeeOrder);
        when(bookingOrderResolver.resolve(bySigns, day)).thenReturn(employeeOrder);

        // when
        dailyReportRestEndpoint.updateBookings(List.of(byId, bySigns));

        // then
        verify(dailyWorkingReportService, times(1)).replaceDailyReports(day, employeeOrder, List.of(byId, bySigns));
    }

    // fixtures

    // deterministic, collision-free ids for fixtures (counter resets per test instance)
    private long nextId = 1;

    private long nextId() {
        return nextId++;
    }

    private Employee employee() {
        Employee res = new Employee();
        ReflectionTestUtils.setField(res, "id", nextId());
        return res;
    }

    private Employeecontract employeeContract(Employee employee) {
        Employeecontract res = new Employeecontract();
        ReflectionTestUtils.setField(res, "id", nextId());
        res.setEmployee(employee);
        return res;
    }

    private Employeeorder employeeOrder(Employeecontract employeeContract) {
        Employeeorder res = new Employeeorder();
        ReflectionTestUtils.setField(res, "id", nextId());
        res.setEmployeecontract(employeeContract);
        return res;
    }
}