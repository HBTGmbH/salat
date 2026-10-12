package de.hbt.salat.etl.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static de.hbt.salat.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static de.hbt.salat.common.exception.ErrorCode.ETL_RUN_NOT_FOUND;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.common.viewhelper.PageNavigation;
import de.hbt.salat.etl.configuration.EtlSwitch;
import de.hbt.salat.etl.domain.ETLDefinitionInRun;
import de.hbt.salat.etl.domain.ETLExecutionHistory;
import de.hbt.salat.etl.domain.ETLRunDetail;
import de.hbt.salat.etl.domain.ETLRunDetail.ExecutionInRun;
import de.hbt.salat.etl.domain.ETLRunHistory;
import de.hbt.salat.etl.domain.ETLRunHistory.Status;
import de.hbt.salat.etl.domain.ETLRunHistory.Trigger;
import de.hbt.salat.etl.service.ETLRunHistoryService;
import de.hbt.salat.etl.service.ETLRunLauncher;
import de.hbt.salat.etl.service.ETLService;
import de.hbt.salat.etl.viewhelper.ETLExecutionViewHelper;

/**
 * Die Seite eines ETL-Laufs mit seinen Ausführungen (#1324). Welche Ausführungen die Person lesen
 * darf, entscheidet {@code ETLRunHistoryService} (dort getestet); die Seite zeigt genau die, in der
 * Reihenfolge, in der sie liefen, und nennt die Zahl der ausgeblendeten.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ETLRunDetailControllerTest {

  private static final PageRequest FIRST_PAGE = PageRequest.of(0, ETLRunHistoryController.EXECUTIONS_PER_PAGE);

  @Mock
  private ETLRunHistoryService etlRunHistoryService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("de/hbt/salat/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);
    var messages = new MessageSourceAccessor(messageSource, Locale.GERMANY);
    var controller = new ETLRunHistoryController(etlRunHistoryService, mock(ETLService.class),
        mock(ETLRunLauncher.class), new EtlSwitch(new SalatProperties()), new ErrorCodeViewHelper(messages),
        messages);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void the_page_shows_the_run_and_a_page_of_the_readable_executions_with_their_number_in_the_run() throws Exception {
    var executions = new PageImpl<>(List.of(
        new ExecutionInRun(1, execution("a", true)),
        new ExecutionInRun(3, execution("b", false))), FIRST_PAGE, 2);
    when(etlRunHistoryService.getRunDetail(7L, false, null, FIRST_PAGE))
        .thenReturn(new ETLRunDetail(run(), executions, List.of(new ETLDefinitionInRun(1L, "a", 1, 0),
            new ETLDefinitionInRun(2L, "b", 1, 1)), 1));

    var result = mockMvc.perform(get("/etl/runs/{id}", 7L))
        .andExpect(view().name("etl/run-detail"))
        .andExpect(model().attribute("hiddenExecutions", 1L))
        .andExpect(model().attribute("readableExecutions", 2L))
        .andExpect(model().attribute("failedExecutions", 1L))
        .andExpect(model().attribute("onlyFailed", false))
        .andReturn();

    @SuppressWarnings("unchecked")
    var shown = (List<ETLExecutionViewHelper>) result.getModelAndView().getModel().get("executions");
    assertThat(shown).extracting(ETLExecutionViewHelper::position).containsExactly(1, 3);
    assertThat(shown).extracting(ETLExecutionViewHelper::definitionName).containsExactly("a", "b");
    assertThat(shown).extracting(ETLExecutionViewHelper::success).containsExactly(true, false);
  }

  @Test
  void filter_and_page_reach_the_service_and_the_links_keep_the_filter() throws Exception {
    // Die Adresse zaehlt ab 1, Spring Data ab 0.
    var third = PageRequest.of(2, ETLRunHistoryController.EXECUTIONS_PER_PAGE);
    when(etlRunHistoryService.getRunDetail(7L, true, 4L, third))
        .thenReturn(new ETLRunDetail(run(), new PageImpl<>(List.of(new ExecutionInRun(120, execution("a", false))),
            third, 300), List.of(new ETLDefinitionInRun(4L, "a", 400, 300)), 0));

    var result = mockMvc.perform(get("/etl/runs/{id}", 7L)
            .param("onlyFailed", "true").param("definitionId", "4").param("page", "3"))
        .andExpect(model().attribute("onlyFailed", true))
        .andExpect(model().attribute("definitionId", 4L))
        .andReturn();

    var pages = (PageNavigation) result.getModelAndView().getModel().get("pages");
    assertThat(pages.getNumber()).isEqualTo(3);
    assertThat(pages.getNextHref()).isEqualTo("/etl/runs/7?onlyFailed=true&definitionId=4&page=4");
    assertThat(pages.getFirstHref()).isEqualTo("/etl/runs/7?onlyFailed=true&definitionId=4");
  }

  @Test
  void a_run_that_no_longer_exists_leads_back_to_the_list_with_a_message() throws Exception {
    when(etlRunHistoryService.getRunDetail(7L, false, null, FIRST_PAGE))
        .thenThrow(new InvalidDataException(ETL_RUN_NOT_FOUND));

    mockMvc.perform(get("/etl/runs/{id}", 7L))
        .andExpect(redirectedUrl("/etl/runs"))
        .andExpect(flash().attribute("toastError", "ETL-Lauf nicht gefunden"));
  }

  @Test
  void a_missing_right_is_not_turned_into_a_message() {
    // Weitergereicht beantwortet AuthorizationExceptionHandler sie mit 403 — eine Umleitung mit
    // Meldung waere das stille Ausweichen, das die Liste auch nicht macht.
    when(etlRunHistoryService.getRunDetail(7L, false, null, FIRST_PAGE))
        .thenThrow(new AuthorizationException(AA_NOT_ATHORIZED));

    assertThatThrownBy(() -> mockMvc.perform(get("/etl/runs/{id}", 7L)))
        .hasCauseInstanceOf(AuthorizationException.class);
  }

  private static ETLRunHistory run() {
    return ETLRunHistory.builder()
        .id(7L)
        .startedAt(LocalDateTime.of(2026, 10, 1, 2, 0))
        .finishedAt(LocalDateTime.of(2026, 10, 1, 2, 3, 12))
        .status(Status.FAILED)
        .triggeredBy(Trigger.SCHEDULED)
        .dateFrom(LocalDate.of(2026, 7, 1))
        .dateUntil(LocalDate.of(2026, 10, 1))
        .message("2 Definition(en) ausgeführt: a, b — fehlgeschlagen: b")
        .build();
  }

  private static ETLExecutionHistory execution(String name, boolean success) {
    return ETLExecutionHistory.builder()
        .etlId(1L)
        .etlName(name)
        .executedAt(LocalDateTime.of(2026, 10, 1, 2, 1))
        .success(success)
        .message("Date Range: 2026-09-01 - 2026-09-30\n")
        .build();
  }

}
