package de.hbt.salat.budget.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.budget.auth.BudgetAuthorization;
import de.hbt.salat.budget.service.BudgetControllingService;
import de.hbt.salat.order.service.CustomerorderService;

/**
 * Links written before #1334 name the order as {@code fCustomerOrderSign} — bookmarks, alert mails and
 * notifications already sent. They are redirected to the id behind the sign; the rest of the query
 * travels along, so a link from an alert mail still lands on an evaluation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class LegacyBudgetFilterControllerTest {

  @Mock
  private CustomerorderService customerorderService;
  @Mock
  private BudgetControllingService budgetControllingService;
  @Mock
  private BudgetAuthorization budgetAuthorization;
  @Mock
  private AuthorizedUser authorizedUser;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(
        new LegacyBudgetFilterController(customerorderService),
        new BudgetControllingController(budgetControllingService, budgetAuthorization, authorizedUser,
            customerorderService)).build();
  }

  @Test
  void translates_the_sign_of_an_alert_mail_into_the_id_and_keeps_the_evaluation() throws Exception {
    when(customerorderService.getCustomerorderIdBySign("1612")).thenReturn(42L);

    mockMvc.perform(get(URI.create("/budget/controlling?fCustomerOrderSign=1612&evaluate=true")))
        .andExpect(redirectedUrl("/budget/controlling?evaluate=true&fBudgetCustomerOrderId=42"));
    verifyNoInteractions(budgetControllingService);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/budget", "/budget/pricing", "/budget/flat-rate", "/budget/controlling"})
  void translates_the_sign_on_every_page_with_the_order_filter(String path) throws Exception {
    when(customerorderService.getCustomerorderIdBySign("1612")).thenReturn(42L);

    mockMvc.perform(get(URI.create(path + "?fCustomerOrderSign=1612")))
        .andExpect(redirectedUrl(path + "?fBudgetCustomerOrderId=42"));
  }

  /** The sign arrives decoded; the parameters around it are passed on as they came, still encoded. */
  @Test
  void reads_an_encoded_sign_and_leaves_the_other_parameters_untouched() throws Exception {
    when(customerorderService.getCustomerorderIdBySign("MUSTER/01 A&B")).thenReturn(7L);

    mockMvc.perform(get(URI.create("/budget?fCustomerOrderSign=MUSTER%2F01%20A%26B&fBudgetShowInactive=true")))
        .andExpect(redirectedUrl("/budget?fBudgetShowInactive=true&fBudgetCustomerOrderId=7"));
  }

  /**
   * Renamed since the link was written: the filter is cleared. Left out, the remembered order would
   * stand in and be evaluated although nobody asked for it.
   */
  @Test
  void clears_the_filter_for_a_sign_that_no_longer_names_an_order() throws Exception {
    when(customerorderService.getCustomerorderIdBySign("1612")).thenReturn(null);

    mockMvc.perform(get(URI.create("/budget/controlling?fCustomerOrderSign=1612&evaluate=true")))
        .andExpect(redirectedUrl("/budget/controlling?evaluate=true&fBudgetCustomerOrderId="));
  }

  @Test
  void leaves_a_request_without_the_old_parameter_to_the_page() throws Exception {
    mockMvc.perform(get(URI.create("/budget/controlling?fBudgetCustomerOrderId=42")))
        .andExpect(status().isOk())
        .andExpect(view().name("budget/controlling"));
    verifyNoInteractions(customerorderService);
  }
}
