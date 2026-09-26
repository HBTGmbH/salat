package org.tb.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.tb.dailyreport.controller.DailyController.dailyViewUrl;
import static org.tb.dailyreport.controller.DailyController.reviewReturnUrlOf;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.dailyreport.domain.DailyViewData;
import org.tb.dailyreport.preferences.DailyPreferenceService;
import org.tb.dailyreport.preferences.DailyPreferences;
import org.tb.dailyreport.service.DailyService;
import org.tb.dailyreport.service.MatrixService;
import org.tb.dailyreport.service.TimereportService;
import org.tb.dailyreport.service.WorkingdayService;
import org.tb.employee.domain.Employee;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.favorites.service.FavoriteService;
import org.tb.order.service.EmployeeorderService;

/**
 * Der Weg aus der Tagesansicht zurück in die Übersicht vor der Freigabe (#760). Deren Tage führen in
 * die Tagesansicht, denn dort werden Arbeitsbeginn und Pause berichtigt, die ihre Befunde nennen.
 * Einen Weg zurück bekommt nur eine Übersicht; die Prüfung ist dieselbe wie die des
 * Buchungsformulars ({@link ReturnUrls}).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class DailyViewReturnTest {

  private static final LocalDate DATE = LocalDate.of(2026, 3, 2);
  private static final String OVERVIEW = "/release/review?until=2026-03&view=day#day-2026-03-02";
  /** The day of {@link #OVERVIEW}, opened from it. */
  private static final String OVERVIEW_DAY = "/dailyreport/daily?mode=daily&date=2026-03-02"
      + "&returnUrl=%2Frelease%2Freview%3Funtil%3D2026-03%26view%3Dday%23day-2026-03-02";

  @Mock private DailyService dailyService;
  @Mock private MatrixService matrixService;
  @Mock private TimereportService timereportService;
  @Mock private WorkingdayService workingdayService;
  @Mock private EmployeecontractService employeecontractService;
  @Mock private EmployeeService employeeService;
  @Mock private FavoriteService favoriteService;
  @Mock private EmployeeorderService employeeorderService;
  @Mock private MessageSourceAccessor messages;
  @Mock private ErrorCodeViewHelper errorCodeViewHelper;
  @Mock private DailyPreferenceService dailyPreferenceService;

  @InjectMocks private DailyController controller;

  @BeforeEach
  void setUp() {
    var loginEmployee = mock(Employee.class);
    when(loginEmployee.getId()).thenReturn(1L);
    when(employeeService.getLoginEmployee()).thenReturn(loginEmployee);
    when(employeecontractService.getCurrentContract(1L)).thenReturn(Optional.empty());
    when(dailyPreferenceService.getForEmployeeContractId(anyLong())).thenReturn(new DailyPreferences(LocalTime.of(9, 0)));
  }

  @ParameterizedTest
  @ValueSource(strings = {OVERVIEW, "/acceptance/release/review?contractId=42&until=2026-03"})
  void a_day_opened_from_an_overview_offers_the_way_back(String returnUrl) {
    var model = new ExtendedModelMap();

    controller.show(null, "daily", DATE, null, null, returnUrl, model);

    assertThat(model.get("reviewReturnUrl")).isEqualTo(returnUrl);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {
      "/dailyreport/daily?mode=daily&date=2026-03-02",
      "/release",
      "/release/reviewer?until=2026-03",
      "javascript:alert(1)",
      "//evil.example.com/release/review"})
  void any_other_target_offers_no_way_back(String returnUrl) {
    var model = new ExtendedModelMap();

    controller.show(null, "daily", DATE, null, null, returnUrl, model);

    assertThat(model.get("reviewReturnUrl")).isNull();
  }

  /** Ohne Skript geht das Formular des Tages als gewöhnlicher POST, und die Umleitung behält den Weg zurück. */
  @Test
  void saving_the_day_without_script_keeps_the_way_back() {
    var form = new WorkingdayForm();
    form.setDate(DATE);
    form.setNotWorked(true);

    var view = controller.saveWorkingday(null, OVERVIEW, form, new MockHttpServletRequest(),
        new MockHttpServletResponse(), new ExtendedModelMap(), new RedirectAttributesModelMap());

    assertThat(view).isEqualTo("redirect:" + OVERVIEW_DAY);
  }

  /** Das Löschen lädt die Seite neu; die Umleitung behält den Weg zurück. */
  @Test
  void deleting_a_booking_keeps_the_way_back() {
    var view = controller.deleteTimereport(7L, DATE, "daily", null, null, OVERVIEW, new RedirectAttributesModelMap());

    assertThat(view).isEqualTo("redirect:" + OVERVIEW_DAY);
  }

  @Test
  void deleting_a_booking_without_an_overview_leads_to_the_plain_day() {
    var view = controller.deleteTimereport(7L, DATE, "daily", null, null, "https://evil.example.com",
        new RedirectAttributesModelMap());

    assertThat(view).isEqualTo("redirect:/dailyreport/daily?mode=daily&date=2026-03-02");
  }

  /**
   * HTMX tauscht die Buchungen des Tages aus, ohne die Seite neu zu laden. Die Anfrage selbst trägt
   * keinen Rücksprung, die Seite, in die das Fragment kommt, aber in ihrer Adresse — und die Verweise
   * darin auf Anlegen, Bearbeiten und Löschen geben ihn weiter.
   */
  @Test
  void a_refreshed_list_of_bookings_keeps_the_way_back() {
    when(dailyService.buildDailyView(DATE, -1L)).thenReturn(mock(DailyViewData.class));
    var model = new ExtendedModelMap();

    controller.deleteFavourite(null, 5L, DATE, htmxRequestFrom("http://localhost:8080" + OVERVIEW_DAY), model);

    assertThat(model.get("reviewReturnUrl")).isEqualTo(OVERVIEW);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "http://localhost:8080/dailyreport/daily?mode=daily&date=2026-03-02",
      "http://localhost:8080/dailyreport/daily?mode=daily&date=2026-03-02&returnUrl=%2Fdailyreport%2Fdaily%3Fmode%3Dlist",
      "http://localhost:8080/dailyreport/daily?mode=daily&date=2026-03-02&returnUrl=https%3A%2F%2Fevil.example.com",
      "http://localhost:8080/dailyreport/daily?mode=daily&date=2026-03-02&returnUrl=%zz",
      "not a url at all"})
  void a_refreshed_list_of_bookings_offers_no_other_way_back(String currentUrl) {
    assertThat(reviewReturnUrlOf(htmxRequestFrom(currentUrl))).isNull();
  }

  @Test
  void a_request_without_a_current_page_offers_no_way_back() {
    assertThat(reviewReturnUrlOf(new MockHttpServletRequest())).isNull();
  }

  @Test
  void the_daily_view_without_an_overview_is_the_plain_day() {
    assertThat(dailyViewUrl(DATE, null)).isEqualTo("/dailyreport/daily?mode=daily&date=2026-03-02");
    assertThat(dailyViewUrl(DATE, "/dailyreport/daily?mode=list")).isEqualTo("/dailyreport/daily?mode=daily&date=2026-03-02");
    assertThat(dailyViewUrl(DATE, "https://evil.example.com")).isEqualTo("/dailyreport/daily?mode=daily&date=2026-03-02");
  }

  private static MockHttpServletRequest htmxRequestFrom(String currentUrl) {
    var request = new MockHttpServletRequest();
    request.addHeader("HX-Request", "true");
    request.addHeader("HX-Current-URL", currentUrl);
    return request;
  }
}
