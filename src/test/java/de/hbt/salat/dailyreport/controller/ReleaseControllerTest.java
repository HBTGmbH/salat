package de.hbt.salat.dailyreport.controller;

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
import static de.hbt.salat.common.exception.ErrorCode.RL_RELEASE_NOT_ALLOWED;
import static de.hbt.salat.common.exception.ErrorCode.RL_REVIEWED_PERIOD_CHANGED;
import static de.hbt.salat.common.exception.ErrorCode.TR_EMPLOYEE_CONTRACT_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.WD_BREAK_TOO_SHORT_6;
import static de.hbt.salat.common.exception.ErrorCode.WD_NOT_WORKED_TIMEREPORTS_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.WD_NO_TIMEREPORT;
import static de.hbt.salat.common.exception.ErrorCode.WD_UPSERT_REQ_EMPLOYEE_OR_MANAGER;
import static de.hbt.salat.common.exception.ErrorCode.XX_CONCURRENT_MODIFICATION;

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
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;
import de.hbt.salat.common.filter.UiStateFilter;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.common.web.LoginSignProvider;
import de.hbt.salat.common.web.UiState;
import de.hbt.salat.common.web.UiStateKeyRegistry;
import de.hbt.salat.dailyreport.domain.ReviewPeriod;
import de.hbt.salat.dailyreport.domain.TimereportReview;
import de.hbt.salat.dailyreport.service.ReleaseService;
import de.hbt.salat.dailyreport.service.WorkingdayService;
import de.hbt.salat.dailyreport.viewhelper.ReviewLinks;
import de.hbt.salat.dailyreport.viewhelper.TimereportReviewViewHelper;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.service.EmployeecontractService;

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
  static final String CONCURRENT_MODIFICATION_TEXT = "Die Daten wurden gleichzeitig an anderer Stelle geändert, "
      + "etwa durch einen doppelten Klick. Bitte prüfe den aktuellen Stand.";

  @Mock private EmployeecontractService employeecontractService;
  @Mock private EmployeeService employeeService;
  @Mock private ReleaseService releaseService;
  @Mock private WorkingdayService workingdayService;
  @Mock private LoginSignProvider loginSignProvider;

  private ReleaseController controller;
  private Employeecontract contract;

  @BeforeEach
  void setUp() {
    var messages = germanMessages();
    controller = new ReleaseController(employeecontractService, employeeService, releaseService, messages,
        new ErrorCodeViewHelper(messages), workingdayService);

    when(loginSignProvider.getEffectiveLoginSign()).thenReturn(LOGIN);
    var loginEmployee = mock(Employee.class);
    when(loginEmployee.getId()).thenReturn(1L);
    when(employeeService.getLoginEmployee()).thenReturn(loginEmployee);
    when(employeecontractService.getCurrentContract(1L)).thenReturn(Optional.empty());
    var contract = mock(Employeecontract.class);
    when(contract.getId()).thenReturn(CONTRACT_ID);
    when(employeecontractService.getEmployeecontractById(CONTRACT_ID)).thenReturn(contract);
    this.contract = contract;
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
    // the header button (#1156) books for the person of the review and leads back into it
    assertThat(result.getModelAndView().getModel().get("newBookingUrl"))
        .isEqualTo("/dailyreport/timereports/new?employeecontractId=42&returnUrl=%2Frelease%2Freview%3Funtil%3D2026-08");
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
   * Ein zweiter Klick schickt die Freigabe ein zweites Mal ab (#1237). Laufen beide gleichzeitig durch
   * den Vergleich des Zeitraums, scheitert die zweite erst beim Schreiben an der Versionsnummer, und
   * {@code ConcurrentModificationAspect} macht daraus {@code XX-0003}. Gespeichert hat die erste; der
   * Toast sagt das, statt nur zu melden, dass nichts freigegeben wurde.
   */
  @Test
  void a_concurrent_release_leads_back_into_the_review_and_says_why() throws Exception {
    doThrow(new BusinessRuleException(XX_CONCURRENT_MODIFICATION))
        .when(releaseService).releaseTimereports(CONTRACT_ID, BEGIN, END);

    perform(release(CONTRACT_ID, BEGIN, END).param("view", "day").cookie(remembered(CONTRACT_ID)))
        .andExpect(redirectedUrl("/release/review?until=2026-08&view=day"))
        .andExpect(flash().attribute("toastError", CONCURRENT_MODIFICATION_TEXT));
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

  /** Ein Klick an einem Tag ohne Buchung markiert ihn und führt an denselben Tag zurück. */
  @Test
  void a_day_without_booking_is_marked_not_worked_and_the_review_shows_that_day_again() throws Exception {
    var day = LocalDate.of(2026, 8, 12);
    var back = "/release/review?until=2026-08&view=day#day-2026-08-12";

    perform(notWorked(CONTRACT_ID, day, back))
        .andExpect(redirectedUrl(back))
        .andExpect(flash().attribute("toastSuccess", "12.08.2026 als nicht gearbeitet markiert."));

    verify(workingdayService).markNotWorked(contract, day);
  }

  /** Auch über die Abnahme: die Geschäftsführung gibt dort für eine andere Person frei. */
  @Test
  void the_review_via_the_acceptance_page_is_a_way_back_as_well() throws Exception {
    var back = "/acceptance/release/review?contractId=42&until=2026-08&view=day#day-2026-08-12";

    perform(notWorked(CONTRACT_ID, LocalDate.of(2026, 8, 12), back))
        .andExpect(redirectedUrl(back));
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://evil.example.com", "//evil.example.com", "/dailyreport/daily?date=2026-08-12", "javascript:alert(1)"})
  void anything_but_a_review_leads_to_the_release_page(String returnUrl) throws Exception {
    perform(notWorked(CONTRACT_ID, LocalDate.of(2026, 8, 12), returnUrl))
        .andExpect(redirectedUrl("/release"));
  }

  /** Eine Buchung, die inzwischen an dem Tag steht, verhindert die Markierung; der Toast sagt warum. */
  @Test
  void a_day_with_a_booking_is_not_marked_and_the_message_says_why() throws Exception {
    var day = LocalDate.of(2026, 8, 12);
    var back = "/release/review?until=2026-08&view=day#day-2026-08-12";
    doThrow(new BusinessRuleException(WD_NOT_WORKED_TIMEREPORTS_FOUND)).when(workingdayService).markNotWorked(contract, day);

    perform(notWorked(CONTRACT_ID, day, back))
        .andExpect(redirectedUrl(back))
        .andExpect(flash().attribute("toastError", "Es wurden Buchungen gefunden, bitte vorher löschen oder verschieben."));
  }

  @Test
  void an_unknown_contract_leads_to_the_release_page() throws Exception {
    perform(notWorked(OTHER_CONTRACT_ID, LocalDate.of(2026, 8, 12), "/release/review?until=2026-08"))
        .andExpect(redirectedUrl("/release"));

    verifyNoInteractions(workingdayService);
  }

  /** Wer den Arbeitstag nicht schreiben darf, bekommt die 403, keinen Toast. */
  @Test
  void a_missing_permission_to_mark_is_not_turned_into_a_message() {
    var day = LocalDate.of(2026, 8, 12);
    doThrow(new AuthorizationException(WD_UPSERT_REQ_EMPLOYEE_OR_MANAGER)).when(workingdayService).markNotWorked(contract, day);

    assertThatThrownBy(() -> perform(notWorked(CONTRACT_ID, day, "/release/review?until=2026-08")))
        .hasRootCauseInstanceOf(AuthorizationException.class);
  }

  private static MockHttpServletRequestBuilder notWorked(long contractId, LocalDate date, String returnUrl) {
    return post("/release/review/not-worked")
        .param("contractId", String.valueOf(contractId))
        .param("date", date.toString())
        .param("returnUrl", returnUrl);
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
    return review(period, true);
  }

  static TimereportReview review(ReviewPeriod period, boolean canCreate) {
    return new TimereportReview(CONTRACT_ID, "Erika Probe", "epr", true, BEGIN.minusDays(1), null, period,
        null, true, Duration.ZERO, List.of(), List.of(), List.of(), List.of(), List.of(), Set.of(), canCreate, true, 0);
  }

  static MessageSourceAccessor germanMessages() {
    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("de/hbt/salat/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);
    return new MessageSourceAccessor(messageSource, Locale.GERMANY);
  }
}
