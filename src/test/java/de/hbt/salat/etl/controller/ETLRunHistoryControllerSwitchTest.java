package de.hbt.salat.etl.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.util.Locale;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.etl.configuration.EtlSwitch;
import de.hbt.salat.etl.service.ETLRunHistoryService;
import de.hbt.salat.etl.service.ETLRunLauncher;
import de.hbt.salat.etl.service.ETLService;

/**
 * Die Seite der ETL-Läufe in einer Umgebung, in der ETL abgeschaltet ist (#1458): sie sagt es und
 * sperrt den Knopf; ein Anstoß, der trotzdem ankommt, bekommt die Meldung des Dienstes.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ETLRunHistoryControllerSwitchTest {

  @Mock private ETLRunHistoryService runHistoryService;
  @Mock private ETLService etlService;
  @Mock private ETLRunLauncher launcher;

  private final SalatProperties properties = new SalatProperties();

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("de/hbt/salat/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);
    var messages = new MessageSourceAccessor(messageSource, Locale.GERMANY);
    var controller = new ETLRunHistoryController(runHistoryService, etlService, launcher, new EtlSwitch(properties),
        new ErrorCodeViewHelper(messages), messages);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void with_the_defaults_the_page_offers_the_start() throws Exception {
    mockMvc.perform(get("/etl/runs"))
        .andExpect(model().attribute("etlEnabled", true));
  }

  @Test
  void switched_off_the_page_says_so() throws Exception {
    properties.getEtl().setEnabled(false);

    mockMvc.perform(get("/etl/runs"))
        .andExpect(model().attribute("etlEnabled", false));
  }

  @Test
  void a_start_turned_away_by_the_service_is_said_on_the_page() throws Exception {
    when(launcher.startManualRun(any(), isNull())).thenThrow(new BusinessRuleException(ErrorCode.ETL_DISABLED));

    mockMvc.perform(post("/etl/runs/run").param("dateFrom", "2026-09-01").param("dateUntil", "2026-09-30"))
        .andExpect(redirectedUrl("/etl/runs"))
        .andExpect(flash().attribute("toastError", Matchers.startsWith("ETL ist in dieser Umgebung abgeschaltet.")));
  }
}
