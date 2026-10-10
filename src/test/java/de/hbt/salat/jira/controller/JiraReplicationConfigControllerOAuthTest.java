package de.hbt.salat.jira.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Locale;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.jira.domain.JiraApiFlavor;
import de.hbt.salat.jira.domain.JiraAuthMethod;
import de.hbt.salat.jira.domain.JiraReplicationConfigInfo;
import de.hbt.salat.jira.service.JiraReplicationConfigService;
import de.hbt.salat.jira.service.JiraReplicationLauncher;
import de.hbt.salat.jira.service.JiraReplicationOAuthService;
import de.hbt.salat.jira.service.JiraReplicationRunService;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

/**
 * The callback of the OAuth connection (#1417): the cookie goes in any case, and the outcome travels
 * in the address of the redirect — the session cookie is {@code SameSite=Strict} and does not come
 * along with a navigation from Atlassian, so a flash attribute would be lost.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraReplicationConfigControllerOAuthTest {

  private static final String CALLBACK = "/jira/replications/oauth/callback";

  @Mock private JiraReplicationConfigService configService;
  @Mock private JiraReplicationOAuthService oauthService;
  @Mock private CustomerorderService customerorderService;
  @Mock private SuborderService suborderService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("de/hbt/salat/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);
    var messages = new MessageSourceAccessor(messageSource, Locale.GERMANY);
    var controller = new JiraReplicationConfigController(configService, mock(JiraReplicationLauncher.class),
        oauthService, mock(JiraReplicationRunService.class), customerorderService, suborderService,
        new ErrorCodeViewHelper(messages), messages);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    when(oauthService.clearedCookie()).thenReturn(ResponseCookie.from("__Host-salat-oauth", "").path("/").maxAge(0).build());
    when(configService.getById(7L)).thenReturn(info());
    when(customerorderService.getSelectableCustomerorders(any())).thenReturn(List.of());
  }

  @Test
  void a_connected_callback_leads_to_the_form_of_the_replication() throws Exception {
    when(oauthService.replicationOf("sealed", "state")).thenReturn(7L);

    mockMvc.perform(get(CALLBACK).param("code", "the-code").param("state", "state")
            .cookie(new Cookie("__Host-salat-oauth", "sealed")))
        .andExpect(redirectedUrl("/jira/replications/7/edit?oauth=connected"))
        .andExpect(header().string("Set-Cookie", Matchers.containsString("__Host-salat-oauth=;")))
        .andExpect(header().string("Set-Cookie", Matchers.containsString("Max-Age=0")));
    verify(oauthService).completeConnection(7L, "sealed", "state", "the-code", null);
  }

  @Test
  void a_callback_without_an_attempt_leads_to_the_list_and_clears_the_cookie() throws Exception {
    when(oauthService.replicationOf(any(), any())).thenThrow(new BusinessRuleException(ErrorCode.SE_OAUTH_STATE_INVALID));

    mockMvc.perform(get(CALLBACK).param("code", "the-code").param("state", "forged"))
        .andExpect(redirectedUrl("/jira/replications?oauth=SE-0006"))
        .andExpect(header().string("Set-Cookie", Matchers.containsString("Max-Age=0")));
    verify(oauthService, never()).completeConnection(anyLong(), any(), any(), any(), any());
  }

  @Test
  void a_refused_code_names_the_error_of_the_provider() throws Exception {
    when(oauthService.replicationOf(any(), any())).thenReturn(7L);
    doThrow(new BusinessRuleException(ErrorCode.SE_OAUTH_TOKEN_REQUEST_FAILED, "invalid_grant"))
        .when(oauthService).completeConnection(anyLong(), any(), any(), any(), any());

    mockMvc.perform(get(CALLBACK).param("code", "the-code").param("state", "state"))
        .andExpect(redirectedUrl("/jira/replications/7/edit?oauth=SE-0008&oauthDetail=invalid_grant"));
  }

  @Test
  void the_form_says_what_came_of_connecting() throws Exception {
    mockMvc.perform(get("/jira/replications/7/edit").param("oauth", "connected"))
        .andExpect(model().attribute("toastSuccess", "Die Replikation ist mit Atlassian verbunden."));
    mockMvc.perform(get("/jira/replications/7/edit").param("oauth", "JI-0045"))
        .andExpect(model().attribute("toastError", Matchers.containsString("https://example.atlassian.net")));
  }

  /** The address can be typed by hand: no text of its own reaches the page. */
  @Test
  void the_form_shows_nothing_of_the_address_but_a_known_code_and_an_oauth_error() throws Exception {
    mockMvc.perform(get("/jira/replications/7/edit").param("oauth", "SE-0008").param("oauthDetail", "Ihr Konto ist gesperrt"))
        .andExpect(model().attribute("toastError", "Der Anbieter hat die Anmeldung abgelehnt (unknown). Bitte noch einmal verbinden."));
    mockMvc.perform(get("/jira/replications/7/edit").param("oauth", "AA-0001"))
        .andExpect(model().attributeDoesNotExist("toastError"));
  }

  private static JiraReplicationConfigInfo info() {
    return new JiraReplicationConfigInfo(7L, "Alpha", 1L, null, "ALPHA", "https://example.atlassian.net",
        JiraApiFlavor.CLOUD, JiraAuthMethod.OAUTH, null, "project = ALPHA", null, null, null, 100, true, false, null,
        false, null, true, null);
  }
}
