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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.tb.common.SalatProperties;
import org.tb.common.filter.UiStateFilter;
import org.tb.common.util.ClockProvider;
import org.tb.common.util.DurationUtilsBean;
import org.tb.common.web.LoginSignProvider;
import org.tb.common.web.UiState;
import org.tb.common.web.UiStateKeyRegistry;
import org.tb.dailyreport.domain.TimereportFilterOptions;
import org.tb.dailyreport.domain.TimereportFilterOptions.CustomerOption;
import org.tb.dailyreport.domain.TimereportFilterOptions.EmployeeOption;
import org.tb.dailyreport.domain.TimereportFilterOptions.OrderOption;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.TimereportFilterSummary;
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
  private ResourceBundleMessageSource messageSource;

  @AfterEach
  void resetClock() {
    ClockProvider.reset();
  }

  @BeforeEach
  void setUp() {
    ClockProvider.useFixedClock(LocalDateTime.of(2026, 9, 27, 12, 5));
    messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("org/tb/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);
    controller = new TimereportListController(timereportListService, excelService,
        new MessageSourceAccessor(messageSource, Locale.GERMANY));

    when(loginSignProvider.getEffectiveLoginSign()).thenReturn(LOGIN);
    when(timereportListService.search(any())).thenReturn(TimereportListResult.empty());
    when(timereportListService.getFilterOptions(anyList(), anyList(), anyList()))
        .thenReturn(TimereportFilterOptions.empty());
    when(timereportListService.describe(any())).thenReturn(
        new TimereportFilterSummary(List.of(), List.of(), List.of(), List.of(), 0, List.of(), true));
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

  /**
   * The block a printout starts with is part of the fragment a filter change swaps in (#1147), and that fragment is
   * rendered without the filter lists. The names therefore have to come from the summary, not from the options —
   * rendered for real here, because a block that reads the options stays empty only once the template runs.
   */
  @Test
  void the_fragment_of_a_filter_change_prints_the_filter_with_names() throws Exception {
    when(timereportListService.describe(any())).thenReturn(new TimereportFilterSummary(
        List.of(new EmployeeOption(5L, "Berta Beispiel", "bb"), new EmployeeOption(6L, "Carl Muster", "cm")),
        List.of(new CustomerOption(3L, "TK", "Testkunde")),
        List.of(new OrderOption(42L, "ORD-42", "Wartung", 3L, "TK", 2)),
        List.of(), 2, List.of("OPS-7"), false));

    var html = renderFragment(get("/dailyreport/list")
        .header("HX-Request", "true")
        .param("fBookingsEmployees", "5,6")
        .param("fBookingsCustomers", "3")
        .param("fBookingsOrders", "42")
        .param("fBookingsTickets", "OPS-7")
        .param("fBookingsTicketChildren", "false")
        .param("fBookingsFrom", "2026-09-01")
        .param("fBookingsUntil", "2026-09-26"));

    assertThat(html)
        .contains("id=\"print-filter\"")
        .contains("01.09.2026 – 26.09.2026")
        .containsSubsequence("<div>Berta Beispiel | bb</div>", "<div>Carl Muster | cm</div>")
        .contains("<div>TK (Testkunde)</div>")
        .containsSubsequence("<div>ORD-42</div>", "(2 Unteraufträge eingeschlossen)</div>")
        .containsSubsequence("<div>OPS-7</div>", "<div>(ohne untergeordnete)</div>")
        .contains("content: \"Buchungsliste · 01.09.2026 – 26.09.2026\";")
        .contains("content: \"gefiltert: 2 Mitarbeiter, 1 Auftraggeber, 1 Auftrag, 1 Ticket\";")
        .contains("content: \"Seite \" counter(page) \" von \" counter(pages);")
        .contains("content: \"Stand: 27.09.2026 12:05\";")
        .contains("<th>Stand</th>\n      <td>27.09.2026 12:05</td>");
    verify(timereportListService, never()).getFilterOptions(anyList(), anyList(), anyList());
  }

  @Test
  void an_unfiltered_list_says_so_in_the_page_margin_and_in_the_block() throws Exception {
    var html = renderFragment(get("/dailyreport/list")
        .header("HX-Request", "true")
        .param("fBookingsFrom", "2026-09-01")
        .param("fBookingsUntil", "2026-09-30"));

    assertThat(html)
        .contains("content: \"ungefiltert\";")
        .contains("<td>alle</td>");
  }

  /**
   * On screen the limit is a warning above the table; on paper it is part of the figures of the result, next to the
   * filter, and says only the numbers. Hours and billability count every hit, not only the ones shown.
   */
  @Test
  void a_limited_list_prints_hits_shown_rows_and_hours_as_figures_of_the_result() throws Exception {
    var booking = TimereportDTO.builder()
        .referenceday(LocalDate.of(2026, 9, 1))
        .employeeName("Berta Beispiel").employeeSign("bb")
        .customerorderSign("ORD-42").customerShortname("TK")
        .completeOrderSign("ORD-42/01").suborderDescription("Wartung")
        .taskdescription("Pflege").duration(Duration.ofMinutes(90)).status("open")
        .build();
    when(timereportListService.search(any())).thenReturn(new TimereportListResult(
        List.of(booking), 3399, Duration.ofHours(9406), Duration.ofHours(4999), 1, 1));

    var html = renderFragment(get("/dailyreport/list")
        .header("HX-Request", "true")
        .param("fBookingsFrom", "2026-09-01")
        .param("fBookingsUntil", "2026-09-26")
        .param("fBookingsLimit", "50"));

    assertThat(html)
        .containsSubsequence("<caption>Filter</caption>", "<caption>Ergebnis</caption>")
        .containsSubsequence("<th>Treffer</th>", "3.399</td>")
        .containsSubsequence("<th>Davon angezeigt</th>", ">1</td>")
        .containsSubsequence("<th>Stunden</th>", ">9.406:00</td>")
        .containsSubsequence("<th>Fakturierbar</th>", ">4.999:00</td>")
        .containsSubsequence("<th>Nicht fakturierbar</th>", ">4.407:00</td>")
        .doesNotContain("Maximale Anzahl Treffer</th>")
        .contains("alert alert-warning m-0 rounded-0 border-start-0 border-end-0 border-top-0 d-print-none");
  }

  /** Like {@link #perform}, but through Thymeleaf, so that the template itself runs. */
  private String renderFragment(MockHttpServletRequestBuilder request) throws Exception {
    var templateResolver = new ClassLoaderTemplateResolver();
    templateResolver.setPrefix("templates/");
    templateResolver.setSuffix(".html");
    templateResolver.setCharacterEncoding("UTF-8");
    var templateEngine = new SpringTemplateEngine();
    templateEngine.setTemplateResolver(templateResolver);
    templateEngine.setTemplateEngineMessageSource(messageSource);

    var viewResolver = new ThymeleafViewResolver();
    viewResolver.setTemplateEngine(templateEngine);
    viewResolver.setCharacterEncoding("UTF-8");

    var uiStateFilter = new UiStateFilter(new UiState(),
        new UiStateKeyRegistry(List.of(new DailyReportUiStateKeyContributor())),
        loginSignProvider, new SalatProperties());
    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setViewResolvers(viewResolver)
        .setLocaleResolver(new FixedLocaleResolver(Locale.GERMANY))
        .addFilters(uiStateFilter)
        .build();
    // Die Vorlage liest @durationUtils. Der Standalone-Aufbau rendert gegen einen Stub-Kontext ohne
    // Bohnen; dessen addBean ist nicht oeffentlich erreichbar, die Klasse ist paketprivat.
    ReflectionTestUtils.invokeMethod(viewResolver.getApplicationContext(), "addBean", "durationUtils",
        new DurationUtilsBean());
    return cssUnescaped(mockMvc.perform(request).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString(UTF_8));
  }

  /**
   * Thymeleaf escapes an inlined CSS value the way an identifier is escaped ({@code \B7\ } for the middle dot), and
   * inside quotes a browser reads those escapes back as the characters they stand for. The assertions read what the
   * browser reads.
   */
  private static String cssUnescaped(String html) {
    return Pattern.compile("\\\\(?:([0-9A-Fa-f]{1,6}) ?|(.))").matcher(html).replaceAll(match ->
        Matcher.quoteReplacement(match.group(1) != null
            ? Character.toString(Integer.parseInt(match.group(1), 16))
            : match.group(2)));
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
