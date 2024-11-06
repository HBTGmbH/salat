package org.tb.dailyreport.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tb.dailyreport.rest.DailyReportData.valueOf;

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
import org.tb.auth.AuthorizedUser;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.service.TimereportService;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.domain.Employeeorder;
import org.tb.order.persistence.EmployeeorderDAO;

@ExtendWith(MockitoExtension.class)
class DailyReportRestEndpointTest {
    @Mock
    EmployeeorderDAO employeeorderDAO;

    @Mock
    EmployeecontractDAO employeecontractDAO;

    @Mock
    TimereportDAO timereportDAO;

    @Mock
    TimereportService timereportService;

    @Mock
    AuthorizedUser authorizedUser;

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
        var employeeContract = employeeContract();

        when(authorizedUser.getEmployeeId()).thenReturn(1L);
        when(authorizedUser.isAuthenticated()).thenReturn(true);
        when(employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(authorizedUser.getEmployeeId(), day))
                .thenReturn(employeeContract);
        when(timereportDAO.getTimereportsByDateAndEmployeeContractId(employeeContract.getId(), day))
                .thenReturn(List.of(timeReport1));
        when(timereportDAO.getTimereportsByDateAndEmployeeContractId(employeeContract.getId(), day.plusDays(1)))
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
        var employeeContract = employeeContract();

        when(authorizedUser.getEmployeeId()).thenReturn(1L);
        when(authorizedUser.isAuthenticated()).thenReturn(true);
        when(employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(eq(authorizedUser.getEmployeeId()), dateArgumentCaptor.capture()))
                .thenReturn(employeeContract);
        when(timereportDAO.getTimereportsByDateAndEmployeeContractId(eq(employeeContract.getId()),  dateArgumentCaptor.capture()))
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
        var employeeContract = employeeContract();
        var employeeOrder = employeeOrder(employeeContract);
        var timeReport1 = TimereportDTO.builder()
                .employeeorderId(1L).referenceday(day)
                .taskdescription("test").duration(Duration.ofHours(1))
                .build();

        when(authorizedUser.isAuthenticated()).thenReturn(true);
        when(employeeorderDAO.getEmployeeorderById(1L))
                .thenReturn(employeeOrder);

        // when
        dailyReportRestEndpoint.createBooking(valueOf(timeReport1));

        // then
        verify(timereportService, times(1)).createTimereports(authorizedUser,
                employeeContract.getId(), employeeOrder.getId(), day, "test",
                false,1, 0, 1);
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
        var employeeContract = employeeContract();
        var employeeOrder = employeeOrder(employeeContract);
        var timeReport1 = TimereportDTO.builder()
                .employeeorderId(1L).referenceday(day)
                .taskdescription("test").duration(Duration.ofHours(1))
                .build();

        when(authorizedUser.isAuthenticated()).thenReturn(true);
        when(employeeorderDAO.getEmployeeorderById(1L))
                .thenReturn(employeeOrder);

        // when
        dailyReportRestEndpoint.createBookings(List.of(valueOf(timeReport1)));

        // then
        verify(timereportService, times(1)).createTimereports(authorizedUser,
                employeeContract.getId(), employeeOrder.getId(), day, "test",
                false,1, 0, 1);
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
        var employeeContract = employeeContract();
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
        when(employeeorderDAO.getEmployeeorderById(1L))
                .thenReturn(employeeOrder1);
        when(employeeorderDAO.getEmployeeorderById(2L))
                .thenReturn(employeeOrder2);

        // when
        dailyReportRestEndpoint.updateBookings(List.of(
                valueOf(timeReport1),
                valueOf(timeReport2),
                valueOf(timeReport3)
        ));

        // then
        verify(timereportService, times(1)).deleteTimeReports(day, 1L, authorizedUser);
        verify(timereportService, times(1)).createTimereports(authorizedUser,
                employeeContract.getId(), employeeOrder1.getId(), day, "test1",
                false,1, 0, 1);
        verify(timereportService, times(1)).deleteTimeReports(day, 2L, authorizedUser);
        verify(timereportService, times(1)).createTimereports(authorizedUser,
                employeeContract.getId(), employeeOrder2.getId(), day, "test2",
                false,1, 0, 1);
        verify(timereportService, times(1)).createTimereports(authorizedUser,
                employeeContract.getId(), employeeOrder2.getId(), day, "test3",
                false,1, 0, 1);
    }

    // fixtures

    private Employeecontract employeeContract() {
        Employeecontract res = new Employeecontract();
        ReflectionTestUtils.setField(res, "id", (long) (Math.random() * 100));
        return res;
    }

    private Employeeorder employeeOrder(Employeecontract employeeContract) {
        Employeeorder res = new Employeeorder();
        ReflectionTestUtils.setField(res, "id", (long) (Math.random() * 100));
        res.setEmployeecontract(employeeContract);
        return res;
    }
}