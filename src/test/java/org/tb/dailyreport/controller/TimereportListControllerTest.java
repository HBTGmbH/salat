package org.tb.dailyreport.controller;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tb.common.SalatProperties;
import org.tb.common.filter.UiStateFilter;
import org.tb.common.web.LoginSignProvider;
import org.tb.common.web.UiState;
import org.tb.common.web.UiStateKeyRegistry;
import org.tb.dailyreport.domain.TimereportFilterOptions;
import org.tb.dailyreport.domain.TimereportListFilter;
import org.tb.dailyreport.domain.TimereportListFilter.Billable;
import org.tb.dailyreport.domain.TimereportListFilter.Sort;
import org.tb.dailyreport.domain.TimereportListResult;
import org.tb.dailyreport.service.TimereportListExcelService;
import org.tb.dailyreport.service.TimereportListService;

/**
 * Opening the booking list without parameters, with a filter remembered from an earlier visit (#1127).
 *
 * <p>The request runs through the real {@link UiStateFilter}, because that is where a page call gets its filter: the
 * remembered values come back as fallback parameters, and the controller cannot tell them from submitted ones. That is
 * the point — a page call must search exactly what a submit with the same filter searches, nothing wider and nothing
 * more expensive.
 *
 * <p>The filter lists are what a page call costs on top of a submit, and only there: the fragment a filter change
 * swaps in does not render them, so it does not compute them either.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
class TimereportListControllerTest {

  private static final String LOGIN = "alice";

  @Mock
  private TimereportListService timereportListService;
  @Mock
  private TimereportListExcelService excelService;
  @Mock
  private LoginSignProvider loginSignProvider;

  private TimereportListController controller;

  @BeforeEach
  void setUp() {
    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("org/tb/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);
    controller = new TimereportListController(timereportListService, excelService,
        new MessageSourceAccessor(messageSource, Locale.GERMANY));

    when(loginSignProvider.getEffectiveLoginSign()).thenReturn(LOGIN);
    when(timereportListService.search(any())).thenReturn(TimereportListResult.empty());
    when(timereportListService.getFilterOptions(anyList(), anyList(), anyList()))
        .thenReturn(TimereportFilterOptions.empty());
  }

  @Test
  void a_page_call_without_parameters_searches_with_the_remembered_filter() throws Exception {
    perform(get("/dailyreport/list").cookie(remembered()));

    assertThat(searchedFilters()).containsExactly(new TimereportListFilter(
        List.of(5L, 7L), List.of(), List.of(42L), List.of(), List.of(), true,
        LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), Billable.BILLABLE, Sort.EMPLOYEE, true,
        TimereportListFilter.UNLIMITED));
  }

  @Test
  void a_page_call_searches_what_a_submit_with_the_same_filter_searches() throws Exception {
    perform(get("/dailyreport/list").cookie(remembered()));
    perform(get("/dailyreport/list")
        .header("HX-Request", "true")
        .param("fBookingsEmployees", "5,7")
        .param("fBookingsOrders", "42")
        .param("fBookingsFrom", "2025-01-01")
        .param("fBookingsUntil", "2025-12-31")
        .param("fBookingsBillable", "BILLABLE")
        .param("fBookingsSort", "-EMPLOYEE")
        .param("fBookingsLimit", "0"));

    var filters = searchedFilters();
    assertThat(filters).hasSize(2);
    assertThat(filters.get(0)).isEqualTo(filters.get(1));
  }

  @Test
  void a_page_call_computes_the_filter_lists_once() throws Exception {
    perform(get("/dailyreport/list").cookie(remembered()))
        .andExpect(view().name("dailyreport/timereport-list"));

    verify(timereportListService, times(1)).getFilterOptions(List.of(42L), List.of(), List.of());
  }

  @Test
  void a_filter_change_does_not_compute_the_filter_lists() throws Exception {
    perform(get("/dailyreport/list").cookie(remembered()).header("HX-Request", "true"))
        .andExpect(view().name("dailyreport/timereport-list :: results"));

    verify(timereportListService, never()).getFilterOptions(anyList(), anyList(), anyList());
  }

  private ResultActions perform(MockHttpServletRequestBuilder request)
      throws Exception {
    // UiState lebt je Anfrage; jede Anfrage bekommt deshalb ihren eigenen Filter und ihren eigenen Zustand.
    var uiStateFilter = new UiStateFilter(new UiState(),
        new UiStateKeyRegistry(List.of(new DailyReportUiStateKeyContributor())),
        loginSignProvider, new SalatProperties());
    var mockMvc = MockMvcBuilders.standaloneSetup(controller).addFilters(uiStateFilter).build();
    return mockMvc.perform(request).andExpect(status().isOk());
  }

  private List<TimereportListFilter> searchedFilters() {
    var captor = ArgumentCaptor.forClass(TimereportListFilter.class);
    verify(timereportListService, atLeastOnce()).search(captor.capture());
    return captor.getAllValues();
  }

  /** The cookie an earlier visit left behind: the values under their key names, bound to the login. */
  private static Cookie remembered() {
    var value = "_ls=" + LOGIN
        + "&bookings.Employees=5,7"
        + "&bookings.Orders=42"
        + "&bookings.From=2025-01-01"
        + "&bookings.Until=2025-12-31"
        + "&bookings.Billable=BILLABLE"
        + "&bookings.Sort=-EMPLOYEE"
        + "&bookings.Limit=0";
    return new Cookie("salat_uistate", Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(UTF_8)));
  }
}
