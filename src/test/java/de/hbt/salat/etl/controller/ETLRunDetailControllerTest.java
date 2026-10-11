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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.etl.domain.ETLExecutionHistory;
import de.hbt.salat.etl.domain.ETLRunDetail;
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
        mock(ETLRunLauncher.class), new ErrorCodeViewHelper(messages), messages);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void the_page_shows_the_run_and_the_readable_executions_numbered_in_their_order() throws Exception {
    var run = ETLRunHistory.builder()
        .id(7L)
        .startedAt(LocalDateTime.of(2026, 10, 1, 2, 0))
        .finishedAt(LocalDateTime.of(2026, 10, 1, 2, 3, 12))
        .status(Status.FAILED)
        .triggeredBy(Trigger.SCHEDULED)
        .dateFrom(LocalDate.of(2026, 7, 1))
        .dateUntil(LocalDate.of(2026, 10, 1))
        .message("2 Definition(en) ausgeführt: a, b — fehlgeschlagen: b")
        .build();
    var first = execution("a", true);
    var second = execution("b", false);
    when(etlRunHistoryService.getRunDetail(7L)).thenReturn(new ETLRunDetail(run, List.of(first, second), 1));

    var result = mockMvc.perform(get("/etl/runs/{id}", 7L))
        .andExpect(view().name("etl/run-detail"))
        .andExpect(model().attribute("hiddenExecutions", 1))
        .andReturn();

    @SuppressWarnings("unchecked")
    var executions = (List<ETLExecutionViewHelper>) result.getModelAndView().getModel().get("executions");
    assertThat(executions).extracting(ETLExecutionViewHelper::position).containsExactly(1, 2);
    assertThat(executions).extracting(ETLExecutionViewHelper::definitionName).containsExactly("a", "b");
    assertThat(executions).extracting(ETLExecutionViewHelper::success).containsExactly(true, false);
  }

  @Test
  void a_run_that_no_longer_exists_leads_back_to_the_list_with_a_message() throws Exception {
    when(etlRunHistoryService.getRunDetail(7L)).thenThrow(new InvalidDataException(ETL_RUN_NOT_FOUND));

    mockMvc.perform(get("/etl/runs/{id}", 7L))
        .andExpect(redirectedUrl("/etl/runs"))
        .andExpect(flash().attribute("toastError", "ETL-Lauf nicht gefunden"));
  }

  @Test
  void a_missing_right_is_not_turned_into_a_message() {
    // Weitergereicht beantwortet AuthorizationExceptionHandler sie mit 403 — eine Umleitung mit
    // Meldung waere das stille Ausweichen, das die Liste auch nicht macht.
    when(etlRunHistoryService.getRunDetail(7L)).thenThrow(new AuthorizationException(AA_NOT_ATHORIZED));

    assertThatThrownBy(() -> mockMvc.perform(get("/etl/runs/{id}", 7L)))
        .hasCauseInstanceOf(AuthorizationException.class);
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
