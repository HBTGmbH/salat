package org.tb.dailyreport.controller;

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
import static org.tb.common.exception.ErrorCode.RL_ACCEPTANCE_DATE_AFTER_RELEASE;
import static org.tb.common.exception.ErrorCode.RL_ACCEPT_NOT_ALLOWED;
import static org.tb.common.exception.ErrorCode.RL_REVIEWED_PERIOD_CHANGED;
import static org.tb.common.exception.ErrorCode.WD_NO_TIMEREPORT;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.dailyreport.domain.ReviewPeriod;
import org.tb.dailyreport.service.ReleaseService;
import org.tb.dailyreport.viewhelper.ReviewLinks;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;

/**
 * Die Freigabe für eine andere Person über die Abnahme (#760): dieselbe Übersicht wie bei der
 * eigenen Freigabe, nur nennt die Adresse den Vertrag, und zurück geht es zur Abnahme. Dazu die
 * Abnahme selbst über ihre Übersicht (#1122) und der Monat, den die Seite der Abnahme vorschlägt.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class AcceptanceControllerTest {

  private static final long CONTRACT_ID = 42L;
  private static final LocalDate BEGIN = LocalDate.of(2026, 8, 1);
  private static final LocalDate END = LocalDate.of(2026, 8, 31);

  @Mock private EmployeecontractService employeecontractService;
  @Mock private EmployeeService employeeService;
  @Mock private ReleaseService releaseService;
  @Mock private AuthorizedUser authorizedUser;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    var messages = ReleaseControllerTest.germanMessages();
    var controller = new AcceptanceController(employeecontractService, employeeService, releaseService,
        authorizedUser, messages, new ErrorCodeViewHelper(messages));
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    when(employeecontractService.getEmployeecontractById(CONTRACT_ID)).thenReturn(mock(Employeecontract.class));
    when(releaseService.reviewRelease(anyLong(), any()))
        .thenReturn(ReleaseControllerTest.review(new ReviewPeriod(BEGIN, END)));
    when(releaseService.reviewAcceptance(anyLong(), any()))
        .thenReturn(ReleaseControllerTest.review(new ReviewPeriod(BEGIN, END)));
  }

  @Test
  void a_review_without_a_contract_goes_back_to_the_acceptance_page() throws Exception {
    mockMvc.perform(get("/acceptance/release/review").param("until", "2026-08"))
        .andExpect(redirectedUrl("/acceptance"));

    verifyNoInteractions(releaseService);
  }

  @Test
  void a_review_without_a_month_goes_back_to_the_acceptance_page() throws Exception {
    mockMvc.perform(get("/acceptance/release/review").param("contractId", "42").param("until", "later"))
        .andExpect(redirectedUrl("/acceptance"));

    verifyNoInteractions(releaseService);
  }

  @Test
  void a_review_of_an_unknown_contract_goes_back_to_the_acceptance_page() throws Exception {
    mockMvc.perform(get("/acceptance/release/review").param("contractId", "7").param("until", "2026-08"))
        .andExpect(redirectedUrl("/acceptance"));

    verifyNoInteractions(releaseService);
  }

  @Test
  void the_review_shows_the_chosen_person_and_month_and_names_the_contract_in_its_addresses() throws Exception {
    var result = mockMvc.perform(get("/acceptance/release/review")
            .param("contractId", "42").param("until", "2026-08").param("view", "day"))
        .andExpect(status().isOk())
        .andExpect(view().name("dailyreport/release-review"))
        .andExpect(model().attribute("reviewView", "day"))
        .andExpect(model().attribute("section", "backoffice"))
        .andExpect(model().attribute("subSection", "acceptance"))
        .andReturn();

    verify(releaseService).reviewRelease(CONTRACT_ID, END);
    assertThat(result.getModelAndView().getModel().get("reviewLinks")).isEqualTo(new ReviewLinks(
        "/acceptance/release/review?contractId=42&until=2026-08&view=day",
        "/acceptance/release/review?contractId=42&until=2026-08",
        "/acceptance/release/review?contractId=42&until=2026-08&view=day",
        "/acceptance/release", "/acceptance"));
  }

  /** Ohne Filterparameter: die Auswahl der Abnahme ist gemerkt, Speichern ändert sie nicht (ADR-0023). */
  @Test
  void releasing_goes_back_to_the_acceptance_page_without_a_filter_parameter() throws Exception {
    mockMvc.perform(post("/acceptance/release")
            .param("contractId", "42").param("periodBegin", "2026-08-01").param("periodEnd", "2026-08-31"))
        .andExpect(redirectedUrl("/acceptance"))
        .andExpect(flash().attribute("toastSuccess", "Buchungen vom 01.08.2026 bis 31.08.2026 freigegeben."));

    verify(releaseService).releaseTimereports(CONTRACT_ID, BEGIN, END);
  }

  @Test
  void a_failure_leads_back_into_the_review_of_the_same_person_and_view() throws Exception {
    doThrow(new BusinessRuleException(WD_NO_TIMEREPORT, BEGIN))
        .when(releaseService).releaseTimereports(CONTRACT_ID, BEGIN, END);

    mockMvc.perform(post("/acceptance/release")
            .param("contractId", "42").param("periodBegin", "2026-08-01").param("periodEnd", "2026-08-31")
            .param("view", "day"))
        .andExpect(redirectedUrl("/acceptance/release/review?contractId=42&until=2026-08&view=day"))
        .andExpect(flash().attribute("toastError", "Die Buchungen wurden nicht freigegeben."));
  }

  @Test
  void an_acceptance_review_without_a_contract_goes_back_to_the_acceptance_page() throws Exception {
    mockMvc.perform(get("/acceptance/accept/review").param("until", "2026-08"))
        .andExpect(redirectedUrl("/acceptance"));

    verifyNoInteractions(releaseService);
  }

  @Test
  void an_acceptance_review_without_a_month_goes_back_to_the_acceptance_page() throws Exception {
    mockMvc.perform(get("/acceptance/accept/review").param("contractId", "42").param("until", "later"))
        .andExpect(redirectedUrl("/acceptance"));
    mockMvc.perform(get("/acceptance/accept/review").param("contractId", "42"))
        .andExpect(redirectedUrl("/acceptance"));

    verifyNoInteractions(releaseService);
  }

  @Test
  void an_acceptance_review_of_an_unknown_contract_goes_back_to_the_acceptance_page() throws Exception {
    mockMvc.perform(get("/acceptance/accept/review").param("contractId", "7").param("until", "2026-08"))
        .andExpect(redirectedUrl("/acceptance"));

    verifyNoInteractions(releaseService);
  }

  @Test
  void the_acceptance_review_shows_the_chosen_person_and_month_and_names_the_contract_in_its_addresses()
      throws Exception {
    var result = mockMvc.perform(get("/acceptance/accept/review")
            .param("contractId", "42").param("until", "2026-08").param("view", "day"))
        .andExpect(status().isOk())
        .andExpect(view().name("dailyreport/acceptance-review"))
        .andExpect(model().attribute("reviewView", "day"))
        .andExpect(model().attribute("section", "backoffice"))
        .andExpect(model().attribute("subSection", "acceptance"))
        .andExpect(model().attribute("pageTitle", "Abnahme prüfen"))
        .andReturn();

    verify(releaseService).reviewAcceptance(CONTRACT_ID, END);
    assertThat(result.getModelAndView().getModel().get("reviewLinks")).isEqualTo(new ReviewLinks(
        "/acceptance/accept/review?contractId=42&until=2026-08&view=day",
        "/acceptance/accept/review?contractId=42&until=2026-08",
        "/acceptance/accept/review?contractId=42&until=2026-08&view=day",
        "/acceptance/accept", "/acceptance"));
  }

  /** Ohne Filterparameter: die Auswahl der Abnahme ist gemerkt, Speichern ändert sie nicht (ADR-0023). */
  @Test
  void accepting_accepts_the_posted_period_and_goes_back_to_the_acceptance_page() throws Exception {
    mockMvc.perform(post("/acceptance/accept")
            .param("contractId", "42").param("periodBegin", "2026-08-01").param("periodEnd", "2026-08-31"))
        .andExpect(redirectedUrl("/acceptance"))
        .andExpect(flash().attribute("toastSuccess", "Buchungen vom 01.08.2026 bis 31.08.2026 abgenommen."));

    verify(releaseService).acceptTimereports(CONTRACT_ID, BEGIN, END);
  }

  @Test
  void a_changed_period_leads_back_into_the_same_view_of_the_acceptance_review_and_says_why() throws Exception {
    doThrow(new BusinessRuleException(RL_REVIEWED_PERIOD_CHANGED))
        .when(releaseService).acceptTimereports(CONTRACT_ID, BEGIN, END);

    mockMvc.perform(post("/acceptance/accept")
            .param("contractId", "42").param("periodBegin", "2026-08-01").param("periodEnd", "2026-08-31")
            .param("view", "day"))
        .andExpect(redirectedUrl("/acceptance/accept/review?contractId=42&until=2026-08&view=day"))
        .andExpect(flash().attribute("toastError",
            "Der Zeitraum hat sich geändert, seit die Übersicht angezeigt wurde, etwa durch eine Freigabe oder "
                + "Abnahme in einem anderen Fenster. Bitte prüfe die aktualisierte Übersicht."));
  }

  /** Den Befund zeigt die Übersicht über dem Zeitraum; der Toast sagt nur, dass nichts abgenommen wurde. */
  @Test
  void a_finding_leads_back_into_the_acceptance_review_with_a_single_message() throws Exception {
    doThrow(new BusinessRuleException(RL_ACCEPTANCE_DATE_AFTER_RELEASE))
        .when(releaseService).acceptTimereports(CONTRACT_ID, BEGIN, END);

    mockMvc.perform(post("/acceptance/accept")
            .param("contractId", "42").param("periodBegin", "2026-08-01").param("periodEnd", "2026-08-31"))
        .andExpect(redirectedUrl("/acceptance/accept/review?contractId=42&until=2026-08"))
        .andExpect(flash().attribute("toastError", "Die Buchungen wurden nicht abgenommen."));
  }

  /** Eine fehlende Berechtigung bleibt die Ausnahme, aus der die Fehlerbehandlung eine 403 macht. */
  @Test
  void a_missing_permission_to_accept_is_not_turned_into_a_message() {
    doThrow(new AuthorizationException(RL_ACCEPT_NOT_ALLOWED))
        .when(releaseService).acceptTimereports(CONTRACT_ID, BEGIN, END);

    assertThatThrownBy(() -> mockMvc.perform(post("/acceptance/accept")
            .param("contractId", "42").param("periodBegin", "2026-08-01").param("periodEnd", "2026-08-31")))
        .hasRootCauseInstanceOf(AuthorizationException.class);
  }

  /** Abgenommen wird, was freigegeben ist: vorgeschlagen und höchstens wählbar ist der Monat der Freigabe. */
  @Test
  void the_default_and_the_latest_month_to_accept_are_the_month_of_the_release() throws Exception {
    givenTheSelectedContract(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 5, 31), null);
    when(releaseService.isAcceptAllowed(CONTRACT_ID)).thenReturn(true);

    mockMvc.perform(get("/acceptance").param("fAcceptanceEmployeeContractId", "42"))
        .andExpect(model().attribute("acceptanceDateStr", "2026-08"))
        .andExpect(model().attribute("acceptanceMaxMonthStr", "2026-08"))
        .andExpect(model().attribute("acceptAllowed", true));
  }

  /** Ohne Freigabe bleibt der frühere Vorschlag; die Übersicht sagt dann, dass nichts freigegeben ist. */
  @Test
  void without_a_release_the_page_keeps_its_former_proposal() throws Exception {
    givenTheSelectedContract(null, null, LocalDate.of(2026, 12, 15));
    when(releaseService.isAcceptAllowed(CONTRACT_ID)).thenReturn(true);

    mockMvc.perform(get("/acceptance").param("fAcceptanceEmployeeContractId", "42"))
        .andExpect(model().attribute("acceptanceDateStr", "2026-01"))
        .andExpect(model().attribute("acceptanceMaxMonthStr", "2026-12"));
  }

  /** Die eigenen Buchungen nimmt niemand ab: die Seite bietet dann keine Übersicht an, die in eine 403 führte. */
  @Test
  void the_page_says_whether_the_selected_contract_may_be_accepted() throws Exception {
    givenTheSelectedContract(LocalDate.of(2026, 8, 31), null, null);
    when(releaseService.isAcceptAllowed(CONTRACT_ID)).thenReturn(false);

    mockMvc.perform(get("/acceptance").param("fAcceptanceEmployeeContractId", "42"))
        .andExpect(model().attribute("acceptAllowed", false));
  }

  private void givenTheSelectedContract(LocalDate releasedUntil, LocalDate acceptedUntil, LocalDate validUntil) {
    var loginEmployee = mock(Employee.class);
    when(loginEmployee.getId()).thenReturn(1L);
    when(employeeService.getLoginEmployee()).thenReturn(loginEmployee);
    var employee = mock(Employee.class);
    when(employee.getName()).thenReturn("Erika Probe");
    var contract = mock(Employeecontract.class);
    when(contract.getId()).thenReturn(CONTRACT_ID);
    when(contract.getEmployee()).thenReturn(employee);
    when(contract.getValidFrom()).thenReturn(LocalDate.of(2026, 1, 1));
    when(contract.getValidUntil()).thenReturn(validUntil);
    when(contract.getReportReleaseDate()).thenReturn(releasedUntil);
    when(contract.getReportAcceptanceDate()).thenReturn(acceptedUntil);
    when(employeecontractService.getEmployeecontractById(CONTRACT_ID)).thenReturn(contract);
    when(employeecontractService.getTeamContractsIncludingExpired(1L)).thenReturn(List.of(contract));
  }
}
