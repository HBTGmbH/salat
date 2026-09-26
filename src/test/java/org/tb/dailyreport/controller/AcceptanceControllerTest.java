package org.tb.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import static org.tb.common.exception.ErrorCode.WD_NO_TIMEREPORT;

import java.time.LocalDate;
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
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.dailyreport.domain.ReviewPeriod;
import org.tb.dailyreport.service.ReleaseService;
import org.tb.dailyreport.viewhelper.ReviewLinks;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;

/**
 * Die Freigabe für eine andere Person über die Abnahme (#760): dieselbe Übersicht wie bei der
 * eigenen Freigabe, nur nennt die Adresse den Vertrag, und zurück geht es zur Abnahme.
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
}
