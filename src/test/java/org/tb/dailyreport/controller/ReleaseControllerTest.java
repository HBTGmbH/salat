package org.tb.dailyreport.controller;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.tb.common.exception.ErrorCode.RL_RELEASE_NOT_ALLOWED;
import static org.tb.common.exception.ErrorCode.RL_REVIEWED_PERIOD_CHANGED;
import static org.tb.common.exception.ErrorCode.TR_EMPLOYEE_CONTRACT_NOT_FOUND;
import static org.tb.common.exception.ErrorCode.WD_BREAK_TOO_SHORT_6;
import static org.tb.common.exception.ErrorCode.WD_NO_TIMEREPORT;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tb.common.SalatProperties;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.common.filter.UiStateFilter;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.common.web.LoginSignProvider;
import org.tb.common.web.UiState;
import org.tb.common.web.UiStateKeyRegistry;
import org.tb.dailyreport.domain.ReviewPeriod;
import org.tb.dailyreport.domain.TimereportReview;
import org.tb.dailyreport.service.ReleaseService;
import org.tb.dailyreport.viewhelper.ReviewLinks;
import org.tb.dailyreport.viewhelper.TimereportReviewViewHelper;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;

/**
 * Die eigene Freigabe über die Übersicht (#760): welcher Vertrag und welcher Monat gezeigt werden,
 * wohin die Adressen der Übersicht führen und wohin es nach dem Freigeben geht.
 *
 * <p>Die Anfragen laufen durch den echten {@link UiStateFilter}: der Vertrag der eigenen Freigabe ist
 * die gemerkte Auswahl, und die kommt als Rückfallparameter aus dem Filter, nicht aus der Anfrage.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ReleaseControllerTest {

  private static final String LOGIN = "epr";
  private static final long CONTRACT_ID = 42L;
  private static final long OTHER_CONTRACT_ID = 99L;
  private static final LocalDate BEGIN = LocalDate.of(2026, 8, 1);
  private static final LocalDate END = LocalDate.of(2026, 8, 31);

  @Mock private EmployeecontractService employeecontractService;
  @Mock private EmployeeService employeeService;
  @Mock private ReleaseService releaseService;
  @Mock private LoginSignProvider loginSignProvider;

  private ReleaseController controller;

  @BeforeEach
  void setUp() {
    var messages = germanMessages();
    controller = new ReleaseController(employeecontractService, employeeService, releaseService, messages,
        new ErrorCodeViewHelper(messages));

    when(loginSignProvider.getEffectiveLoginSign()).thenReturn(LOGIN);
    var loginEmployee = mock(Employee.class);
    when(loginEmployee.getId()).thenReturn(1L);
    when(employeeService.getLoginEmployee()).thenReturn(loginEmployee);
    when(employeecontractService.getCurrentContract(1L)).thenReturn(Optional.empty());
    var contract = mock(Employeecontract.class);
    when(contract.getId()).thenReturn(CONTRACT_ID);
    when(employeecontractService.getEmployeecontractById(CONTRACT_ID)).thenReturn(contract);
    when(releaseService.reviewRelease(anyLong(), any())).thenReturn(review(new ReviewPeriod(BEGIN, END)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "2026-13", "August", "2026-08-31"})
  void a_review_without_a_month_goes_back_to_the_choice_of_the_month(String until) throws Exception {
    perform(get("/release/review").param("until", until).cookie(remembered(CONTRACT_ID)))
        .andExpect(redirectedUrl("/release"));

    verifyNoInteractions(releaseService);
  }

  @Test
  void a_review_without_any_month_goes_back_to_the_choice_of_the_month() throws Exception {
    perform(get("/release/review").cookie(remembered(CONTRACT_ID))).andExpect(redirectedUrl("/release"));

    verifyNoInteractions(releaseService);
  }

  @Test
  void a_review_without_a_contract_goes_back_to_the_release_page() throws Exception {
    perform(get("/release/review").param("until", "2026-08")).andExpect(redirectedUrl("/release"));

    verifyNoInteractions(releaseService);
  }

  @Test
  void the_review_shows_the_chosen_month_of_the_remembered_contract_by_order() throws Exception {
    var result = perform(get("/release/review").param("until", "2026-08").cookie(remembered(CONTRACT_ID)))
        .andExpect(status().isOk())
        .andExpect(view().name("dailyreport/release-review"))
        .andExpect(model().attribute("reviewView", "order"))
        .andExpect(model().attribute("section", "dailyreport"))
        .andExpect(model().attribute("subSection", "release"))
        .andReturn();

    verify(releaseService).reviewRelease(CONTRACT_ID, END);
    assertThat(result.getModelAndView().getModel().get("review")).isInstanceOf(TimereportReviewViewHelper.class);
    assertThat(links(result)).isEqualTo(new ReviewLinks(
        "/release/review?until=2026-08",
        "/release/review?until=2026-08",
        "/release/review?until=2026-08&view=day",
        "/release", "/release"));
  }

  @Test
  void the_view_by_day_is_carried_in_the_address_of_the_page() throws Exception {
    var result = perform(get("/release/review").param("until", "2026-08").param("view", "day")
        .cookie(remembered(CONTRACT_ID)))
        .andExpect(model().attribute("reviewView", "day"))
        .andReturn();

    assertThat(links(result).currentUrl()).isEqualTo("/release/review?until=2026-08&view=day");
  }

  @Test
  void any_other_view_is_the_view_by_order() throws Exception {
    perform(get("/release/review").param("until", "2026-08").param("view", "calendar")
        .cookie(remembered(CONTRACT_ID)))
        .andExpect(model().attribute("reviewView", "order"));
  }

  /** Endet der Vertrag mitten im Monat, rechnet eine Rückkehr trotzdem denselben Monat neu. */
  @Test
  void the_addresses_name_the_chosen_month_not_the_clipped_end() throws Exception {
    when(releaseService.reviewRelease(anyLong(), any()))
        .thenReturn(review(new ReviewPeriod(BEGIN, LocalDate.of(2026, 8, 14))));

    var result = perform(get("/release/review").param("until", "2026-08").cookie(remembered(CONTRACT_ID)))
        .andReturn();

    assertThat(links(result).currentUrl()).isEqualTo("/release/review?until=2026-08");
  }

  @Test
  void releasing_releases_the_posted_period_and_names_it() throws Exception {
    perform(release(CONTRACT_ID, BEGIN, END).cookie(remembered(CONTRACT_ID)))
        .andExpect(redirectedUrl("/release"))
        .andExpect(flash().attribute("toastSuccess", "Buchungen vom 01.08.2026 bis 31.08.2026 freigegeben."));

    verify(releaseService).releaseTimereports(CONTRACT_ID, BEGIN, END);
  }

  @Test
  void a_changed_period_leads_back_into_the_same_view_of_the_review_and_says_why() throws Exception {
    doThrow(new BusinessRuleException(RL_REVIEWED_PERIOD_CHANGED))
        .when(releaseService).releaseTimereports(CONTRACT_ID, BEGIN, END);

    perform(release(CONTRACT_ID, BEGIN, END).param("view", "day").cookie(remembered(CONTRACT_ID)))
        .andExpect(redirectedUrl("/release/review?until=2026-08&view=day"))
        .andExpect(flash().attribute("toastError",
            "Der Zeitraum hat sich geändert, seit die Übersicht angezeigt wurde, etwa durch eine Freigabe oder "
                + "Abnahme in einem anderen Fenster. Bitte prüfe die aktualisierte Übersicht."));
  }

  /** Die Befunde stehen am Tag in der Übersicht; der Toast sagt nur, dass nichts freigegeben wurde. */
  @Test
  void findings_lead_back_into_the_review_with_a_single_message() throws Exception {
    doThrow(new BusinessRuleException(List.of(
        ServiceFeedbackMessage.error(WD_NO_TIMEREPORT, BEGIN),
        ServiceFeedbackMessage.error(WD_BREAK_TOO_SHORT_6, END))))
        .when(releaseService).releaseTimereports(CONTRACT_ID, BEGIN, END);

    perform(release(CONTRACT_ID, BEGIN, END).cookie(remembered(CONTRACT_ID)))
        .andExpect(redirectedUrl("/release/review?until=2026-08"))
        .andExpect(flash().attribute("toastError", "Die Buchungen wurden nicht freigegeben."));
  }

  @Test
  void invalid_data_leads_back_into_the_review_as_well() throws Exception {
    doThrow(new InvalidDataException(TR_EMPLOYEE_CONTRACT_NOT_FOUND))
        .when(releaseService).releaseTimereports(CONTRACT_ID, BEGIN, END);

    perform(release(CONTRACT_ID, BEGIN, END).cookie(remembered(CONTRACT_ID)))
        .andExpect(redirectedUrl("/release/review?until=2026-08"))
        .andExpect(flash().attribute("toastError", "Die Buchungen wurden nicht freigegeben."));
  }

  /** Ein Vertrag, der mitten im Monat endet: zurück geht es in die Übersicht über diesen Monat. */
  @Test
  void a_failure_at_the_contract_end_leads_back_into_the_review_of_its_month() throws Exception {
    var contractEnd = LocalDate.of(2026, 8, 14);
    doThrow(new BusinessRuleException(RL_REVIEWED_PERIOD_CHANGED))
        .when(releaseService).releaseTimereports(CONTRACT_ID, BEGIN, contractEnd);

    perform(release(CONTRACT_ID, BEGIN, contractEnd).cookie(remembered(CONTRACT_ID)))
        .andExpect(redirectedUrl("/release/review?until=2026-08"));
  }

  /**
   * Die Übersicht nimmt den Vertrag aus der gemerkten Auswahl. Nennt die inzwischen eine andere
   * Person, etwa nach einem Wechsel in einem zweiten Fenster, zeigte die Übersicht nach dem Scheitern
   * nicht mehr die Person, deren Freigabe gescheitert ist.
   */
  @Test
  void a_failure_for_a_contract_no_longer_remembered_goes_back_to_the_release_page() throws Exception {
    doThrow(new BusinessRuleException(RL_REVIEWED_PERIOD_CHANGED))
        .when(releaseService).releaseTimereports(CONTRACT_ID, BEGIN, END);

    perform(release(CONTRACT_ID, BEGIN, END).cookie(remembered(OTHER_CONTRACT_ID)))
        .andExpect(redirectedUrl("/release"))
        .andExpect(flash().attribute("toastError",
            "Der Zeitraum hat sich geändert, seit die Übersicht angezeigt wurde, etwa durch eine Freigabe oder "
                + "Abnahme in einem anderen Fenster. Bitte prüfe die aktualisierte Übersicht."));
  }

  /**
   * Die Freigabe des Vertrags einer anderen Person gibt der Service nur frei, wer sie freigeben darf.
   * Seine Ablehnung wird nicht zum Toast, sondern bleibt die Ausnahme, aus der die Fehlerbehandlung
   * eine 403 macht (siehe ControllerAuthorizationIntegrationTest).
   */
  @Test
  void a_missing_permission_is_not_turned_into_a_message() {
    doThrow(new AuthorizationException(RL_RELEASE_NOT_ALLOWED))
        .when(releaseService).releaseTimereports(OTHER_CONTRACT_ID, BEGIN, END);

    assertThatThrownBy(() -> perform(release(OTHER_CONTRACT_ID, BEGIN, END).cookie(remembered(CONTRACT_ID))))
        .hasRootCauseInstanceOf(AuthorizationException.class);
  }

  private static MockHttpServletRequestBuilder release(long contractId, LocalDate begin, LocalDate end) {
    return post("/release")
        .param("contractId", String.valueOf(contractId))
        .param("periodBegin", begin.toString())
        .param("periodEnd", end.toString());
  }

  private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
    // UiState lebt je Anfrage; jede Anfrage bekommt deshalb ihren eigenen Filter und ihren eigenen Zustand.
    var uiStateFilter = new UiStateFilter(new UiState(),
        new UiStateKeyRegistry(List.of(new DailyReportUiStateKeyContributor())),
        loginSignProvider, new SalatProperties());
    var mockMvc = MockMvcBuilders.standaloneSetup(controller).addFilters(uiStateFilter).build();
    return mockMvc.perform(request);
  }

  private static ReviewLinks links(MvcResult result) {
    return (ReviewLinks) result.getModelAndView().getModel().get("reviewLinks");
  }

  /** Die gemerkte Auswahl der Tagesansicht, wie ein früherer Besuch sie hinterlassen hat. */
  private static Cookie remembered(long contractId) {
    var value = "_ls=" + LOGIN + "&employeeContract.Id=" + contractId;
    return new Cookie("salat_uistate", Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(UTF_8)));
  }

  static TimereportReview review(ReviewPeriod period) {
    return new TimereportReview(CONTRACT_ID, "Erika Probe", "epr", true, BEGIN.minusDays(1), null, period,
        null, true, Duration.ZERO, List.of(), List.of(), List.of(), List.of(), List.of(), Set.of(), true, true, 0);
  }

  static MessageSourceAccessor germanMessages() {
    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("org/tb/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);
    return new MessageSourceAccessor(messageSource, Locale.GERMANY);
  }
}
