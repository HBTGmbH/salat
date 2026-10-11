package de.hbt.salat.jira.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.jira.configuration.JiraSwitch;
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
 * The page of the replications in an environment whose JIRA connection is switched off (#1458): it
 * says so, offers no start and no field catalogue, and a start that gets through anyway is answered
 * with the message of the service.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraReplicationConfigControllerSwitchTest {

  @Mock private JiraReplicationConfigService configService;
  @Mock private JiraReplicationLauncher launcher;
  @Mock private JiraReplicationOAuthService oauthService;
  @Mock private JiraReplicationRunService runService;
  @Mock private CustomerorderService customerorderService;
  @Mock private SuborderService suborderService;

  private final SalatProperties properties = new SalatProperties();

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("de/hbt/salat/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);
    var messages = new MessageSourceAccessor(messageSource, Locale.GERMANY);
    var controller = new JiraReplicationConfigController(configService, launcher, oauthService, runService,
        new JiraSwitch(properties), customerorderService, suborderService, new ErrorCodeViewHelper(messages),
        messages);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    when(configService.getById(7L)).thenReturn(info());
    when(customerorderService.getSelectableCustomerorders(any())).thenReturn(List.of());
  }

  @Test
  void with_the_defaults_the_list_offers_the_start() throws Exception {
    mockMvc.perform(get("/jira/replications"))
        .andExpect(model().attribute("jiraEnabled", true));
  }

  @Test
  void switched_off_the_list_says_so() throws Exception {
    properties.getJira().setEnabled(false);

    mockMvc.perform(get("/jira/replications"))
        .andExpect(model().attribute("jiraEnabled", false));
  }

  @Test
  void switched_off_the_form_offers_no_field_catalogue() throws Exception {
    properties.getJira().setEnabled(false);

    mockMvc.perform(get("/jira/replications/7/edit"))
        .andExpect(model().attribute("fieldsBlockedReason", "main.jira.replication.fields.button.disabled.jiradisabled"));
  }

  @Test
  void with_the_defaults_the_form_offers_the_field_catalogue() throws Exception {
    mockMvc.perform(get("/jira/replications/7/edit"))
        .andExpect(model().attribute("fieldsBlockedReason", Matchers.nullValue()));
  }

  @Test
  void a_start_turned_away_by_the_service_is_said_on_the_page() throws Exception {
    doThrow(new BusinessRuleException(ErrorCode.JI_DISABLED)).when(launcher).startManualRun(7L);

    mockMvc.perform(post("/jira/replications/7/run"))
        .andExpect(redirectedUrl("/jira/replications#tab-runs"))
        .andExpect(flash().attribute("toastError",
            Matchers.startsWith("Die JIRA-Anbindung ist in dieser Umgebung abgeschaltet.")));
  }

  @Test
  void a_start_of_a_switched_off_replication_is_said_on_the_page() throws Exception {
    doThrow(new BusinessRuleException(ErrorCode.JI_REPLICATION_DISABLED)).when(launcher).startManualRun(7L);

    mockMvc.perform(post("/jira/replications/7/run"))
        .andExpect(flash().attribute("toastError", Matchers.startsWith("Die Replikation ist ausgeschaltet.")));
  }

  /** A server replication with HTTP Basic: the field catalogue has no reason of its own to be blocked. */
  private static JiraReplicationConfigInfo info() {
    return new JiraReplicationConfigInfo(7L, "Alpha", 1L, null, "ALPHA", "https://jira.example.com",
        JiraApiFlavor.SERVER, JiraAuthMethod.BASIC, "jira-user", "project = ALPHA", null, null, null, 100, true,
        false, null, false, null, true, null);
  }
}
