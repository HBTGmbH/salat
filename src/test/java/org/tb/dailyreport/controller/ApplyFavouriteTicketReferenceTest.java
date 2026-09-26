package org.tb.dailyreport.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.tb.dailyreport.domain.DailyViewData;
import org.tb.dailyreport.preferences.DailyPreferenceService;
import org.tb.dailyreport.preferences.DailyPreferences;
import org.tb.dailyreport.service.DailyService;
import org.tb.dailyreport.service.TimereportService;
import org.tb.dailyreport.service.WorkingdayService;
import org.tb.employee.domain.Employee;
import org.tb.employee.service.EmployeeService;
import org.tb.favorites.domain.Favorite;
import org.tb.favorites.service.FavoriteService;

/**
 * Applying a favourite creates the booking it stands for, ticket reference included (#1029). The
 * overload without the reference passes {@code null} on to the booking, so taking it was what made
 * the reference disappear on the way back out.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApplyFavouriteTicketReferenceTest {

  private static final LocalDate DATE = LocalDate.parse("2026-06-18");
  private static final long CONTRACT_ID = 42L;
  private static final long FAVOURITE_ID = 5L;
  private static final long EMPLOYEE_ORDER_ID = 7L;

  @InjectMocks
  private DailyController classUnderTest;
  @Mock
  private DailyService dailyService;
  @Mock
  private TimereportService timereportService;
  @Mock
  private WorkingdayService workingdayService;
  @Mock
  private EmployeeService employeeService;
  @Mock
  private FavoriteService favoriteService;
  @Mock
  private DailyPreferenceService dailyPreferenceService;

  /** Everything the handler renders afterwards; none of it is what this test is about. */
  @BeforeEach
  void theViewItRendersAfterwards() {
    when(dailyPreferenceService.getForEmployeeContractId(anyLong()))
        .thenReturn(new DailyPreferences(LocalTime.of(9, 0)));
    when(dailyService.buildDailyView(any(), anyLong())).thenReturn(mock(DailyViewData.class));
    var employee = mock(Employee.class);
    when(employee.getId()).thenReturn(1L);
    when(employeeService.getLoginEmployee()).thenReturn(employee);
    when(favoriteService.getFavorites(anyLong())).thenReturn(List.of());
  }

  @Test
  void the_reference_of_the_favourite_becomes_the_reference_of_the_booking() {
    givenFavourite(favourite("PROJ-123"));

    applyIt();

    verify(timereportService).createTimereports(eq(CONTRACT_ID), eq(EMPLOYEE_ORDER_ID), eq(DATE),
        eq("Daily"), eq("PROJ-123"), eq(false), anyLong(), anyLong(), anyInt());
  }

  /** A favourite made before this existed has no reference, and none is invented for it. */
  @Test
  void a_favourite_without_a_reference_books_without_one() {
    givenFavourite(favourite(null));

    applyIt();

    verify(timereportService).createTimereports(eq(CONTRACT_ID), eq(EMPLOYEE_ORDER_ID), eq(DATE),
        eq("Daily"), eq(null), eq(false), anyLong(), anyLong(), anyInt());
  }

  private void givenFavourite(Favorite favourite) {
    when(favoriteService.getFavorite(FAVOURITE_ID)).thenReturn(Optional.of(favourite));
  }

  private void applyIt() {
    classUnderTest.applyFavourite(CONTRACT_ID, FAVOURITE_ID, DATE, new MockHttpServletRequest(),
        new MockHttpServletResponse(), new ExtendedModelMap());
  }

  private static Favorite favourite(String ticketReference) {
    return Favorite.builder()
        .employeeorderId(EMPLOYEE_ORDER_ID)
        .hours(1)
        .minutes(30)
        .comment("Daily")
        .ticketReference(ticketReference)
        .build();
  }

}
