package de.hbt.salat.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
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
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import de.hbt.salat.dailyreport.domain.DailyViewData;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.preferences.DailyPreferenceService;
import de.hbt.salat.dailyreport.preferences.DailyPreferences;
import de.hbt.salat.dailyreport.service.DailyService;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.dailyreport.service.WorkingdayService;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.favorites.service.FavoriteService;
import de.hbt.salat.order.domain.TicketReferenceMode;
import de.hbt.salat.order.domain.TicketReferencePolicy;
import de.hbt.salat.order.viewhelper.TicketReferencePolicyViewHelper;

/**
 * The inline edit of a comment in the daily view is held when the comment names ticket keys that are
 * no reference of the booking yet (#1326): the row stays open with the text as typed and offers them,
 * and only the answer saves. Taking over a booking of an earlier day hands all its references on.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class DailyInlineTicketSuggestionTest {

  private static final LocalDate DATE = LocalDate.parse("2026-06-18");
  private static final long TIMEREPORT_ID = 3L;
  private static final long CONTRACT_ID = 42L;
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
  @Mock
  private TicketReferencePolicyViewHelper ticketReferencePolicyViewHelper;

  @BeforeEach
  void setUp() {
    when(dailyPreferenceService.getForEmployeeContractId(anyLong()))
        .thenReturn(new DailyPreferences(LocalTime.of(9, 0), true));
    when(dailyService.buildDailyView(any(), anyLong())).thenReturn(mock(DailyViewData.class));
    var employee = mock(Employee.class);
    when(employee.getId()).thenReturn(1L);
    when(employeeService.getLoginEmployee()).thenReturn(employee);
    when(favoriteService.getFavorites(anyLong())).thenReturn(List.of());
    when(ticketReferencePolicyViewHelper.label(any())).thenReturn("höchstens 2");
    givenBooking("OPS-1");
    when(timereportService.getTicketReferencePolicy(TIMEREPORT_ID))
        .thenReturn(new TicketReferencePolicy(TicketReferenceMode.LIMITED, 2));
  }

  @Test
  void an_edited_comment_naming_a_new_key_is_held_and_offers_it() {
    var model = new ExtendedModelMap();

    editComment("Fehler in abc-12 und OPS-1", null, null, model);

    var suggestion = (InlineTicketSuggestion) model.get("inlineTicketSuggestion");
    assertThat(suggestion.keys()).containsExactly("ABC-12");
    assertThat(suggestion.taskdescription()).isEqualTo("Fehler in abc-12 und OPS-1");
    assertThat(suggestion.remaining()).isEqualTo(1);
    verify(timereportService, never()).updateTimereport(anyLong(), anyLong(), anyLong(), any(), anyString(),
        anyBoolean(), anyLong(), anyLong());
    verify(timereportService, never()).updateTimereport(anyLong(), anyLong(), anyLong(), any(), anyString(),
        any(List.class), anyBoolean(), anyLong(), anyLong());
  }

  @Test
  void a_comment_without_new_keys_saves_at_once() {
    var model = new ExtendedModelMap();

    editComment("nur Text zu OPS-1", null, null, model);

    assertThat(model.get("inlineTicketSuggestion")).isNull();
    verify(timereportService).updateTimereport(TIMEREPORT_ID, CONTRACT_ID, EMPLOYEE_ORDER_ID, DATE,
        "nur Text zu OPS-1", false, 1, 0);
  }

  @Test
  void the_ticked_keys_are_adopted_on_answer() {
    editComment("Fehler in ABC-12", "adopt", List.of("ABC-12"), new ExtendedModelMap());

    verify(timereportService).updateTimereport(TIMEREPORT_ID, CONTRACT_ID, EMPLOYEE_ORDER_ID, DATE,
        "Fehler in ABC-12", List.of("OPS-1", "ABC-12"), false, 1, 0);
  }

  @Test
  void saving_without_adopting_keeps_the_references() {
    editComment("Fehler in ABC-12", "skip", null, new ExtendedModelMap());

    verify(timereportService).updateTimereport(TIMEREPORT_ID, CONTRACT_ID, EMPLOYEE_ORDER_ID, DATE,
        "Fehler in ABC-12", false, 1, 0);
  }

  @Test
  void nothing_is_offered_once_the_limit_is_reached() {
    givenBooking("OPS-1", "OPS-2");

    editComment("Fehler in ABC-12", null, null, new ExtendedModelMap());

    verify(timereportService).updateTimereport(TIMEREPORT_ID, CONTRACT_ID, EMPLOYEE_ORDER_ID, DATE,
        "Fehler in ABC-12", false, 1, 0);
  }

  /** Editing the duration says nothing about the comment, so nothing is offered. */
  @Test
  void an_edited_duration_is_never_held() {
    classUnderTest.updateTimereportInline(TIMEREPORT_ID, "2:00", null, null, null, htmxRequest(),
        new MockHttpServletResponse(), new ExtendedModelMap());

    verify(timereportService).updateTimereport(eq(TIMEREPORT_ID), eq(CONTRACT_ID), eq(EMPLOYEE_ORDER_ID), eq(DATE),
        anyString(), eq(false), eq(2L), eq(0L));
  }

  @Test
  void taking_over_an_earlier_booking_hands_all_its_references_on() {
    classUnderTest.applyPrevious(CONTRACT_ID, EMPLOYEE_ORDER_ID, "Review", List.of("ABC-1", "ABC-2"), 90, DATE,
        new MockHttpServletRequest(), new MockHttpServletResponse(), new ExtendedModelMap());

    verify(timereportService).createTimereports(eq(CONTRACT_ID), eq(EMPLOYEE_ORDER_ID), eq(DATE), eq("Review"),
        eq(List.of("ABC-1", "ABC-2")), eq(false), eq(1L), eq(30L), anyInt());
  }

  private void editComment(String comment, String choice, List<String> adopted, ExtendedModelMap model) {
    classUnderTest.updateTimereportInline(TIMEREPORT_ID, null, comment, choice, adopted, htmxRequest(),
        new MockHttpServletResponse(), model);
  }

  private static MockHttpServletRequest htmxRequest() {
    var request = new MockHttpServletRequest();
    request.addHeader("HX-Request", "true");
    request.addHeader("HX-Current-URL", "/dailyreport/daily?mode=daily&date=" + DATE);
    return request;
  }

  private void givenBooking(String... references) {
    when(timereportService.getTimereportById(TIMEREPORT_ID)).thenReturn(TimereportDTO.builder()
        .id(TIMEREPORT_ID)
        .referenceday(DATE)
        .employeecontractId(CONTRACT_ID)
        .employeeorderId(EMPLOYEE_ORDER_ID)
        .duration(Duration.ofHours(1))
        .taskdescription("alt")
        .ticketReferences(List.of(references))
        .build());
  }
}
