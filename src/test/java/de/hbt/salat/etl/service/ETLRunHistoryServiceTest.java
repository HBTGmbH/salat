package de.hbt.salat.etl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static de.hbt.salat.auth.domain.AccessLevel.EXECUTE;
import static de.hbt.salat.auth.domain.AccessLevel.READ;
import static de.hbt.salat.common.exception.ErrorCode.ETL_RUN_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.ETL_RUN_NOT_RUNNING;
import static de.hbt.salat.etl.domain.ETLRunHistory.Status.FAILED;
import static de.hbt.salat.etl.domain.ETLRunHistory.Status.RUNNING;
import static de.hbt.salat.etl.domain.ETLRunHistory.Status.SUCCEEDED;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;
import de.hbt.salat.etl.auth.ETLAuthorization;
import de.hbt.salat.etl.domain.ETLDefinitionInRun;
import de.hbt.salat.etl.domain.ETLExecutionHistory;
import de.hbt.salat.etl.domain.ETLRunDetail.ExecutionInRun;
import de.hbt.salat.etl.domain.ETLRunHistory;
import de.hbt.salat.etl.domain.ETLRunHistory.Status;
import de.hbt.salat.etl.domain.ETLRunHistory.Trigger;
import de.hbt.salat.etl.persistence.ETLExecutionHistoryRepository;
import de.hbt.salat.etl.persistence.ETLRunHistoryRepository;

/**
 * Wer die Laufhistorie sehen darf (#573): Geschäftsführung und Administration — das entscheidet
 * {@link ETLAuthorization} — und darüber hinaus jede Anmeldung mit einer Regel der Kategorie
 * {@code ETL} und {@code EXECUTE}. Dieselbe Linie entscheidet seit #1071 darüber, wer einen
 * hängengebliebenen Lauf als beendet markieren darf.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ETLRunHistoryServiceTest {

  @Mock
  private ETLRunHistoryRepository runHistoryRepository;

  @Mock
  private ETLExecutionHistoryRepository executionHistoryRepository;

  @Mock
  private ETLAuthorization etlAuthorization;

  @InjectMocks
  private ETLRunHistoryService etlRunHistoryService;

  @Test
  void an_execute_rule_is_enough_to_see_the_runs() {
    when(etlAuthorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(true);
    when(runHistoryRepository.findByOrderByStartedAtDesc(any())).thenReturn(List.of(new ETLRunHistory()));

    assertThat(etlRunHistoryService.getLatestRuns(100, false)).hasSize(1);
  }

  @Test
  void without_a_rule_the_list_is_refused_rather_than_empty() {
    // Leere Liste statt Absage waere eine Falschaussage: es gibt Laeufe, nur nicht fuer diese Augen.
    when(etlAuthorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(false);

    assertThatThrownBy(() -> etlRunHistoryService.getLatestRuns(100, false))
        .isInstanceOf(AuthorizationException.class);

    verify(runHistoryRepository, never()).findByOrderByStartedAtDesc(any());
  }

  @Test
  void the_filter_asks_for_everything_that_did_not_succeed() {
    when(etlAuthorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(true);

    etlRunHistoryService.getLatestRuns(25, true);

    verify(runHistoryRepository).findByStatusNotOrderByStartedAtDesc(eq(SUCCEEDED), any());
  }

  // --- Einen hängengebliebenen Lauf als beendet markieren (#1071) -----------------------------

  @Test
  void without_a_rule_nobody_marks_a_run_as_finished() {
    // Die Marke hebt die Sperre auf. Wer die Läufe nicht einmal sehen darf, entscheidet auch nicht
    // darüber, ob wieder einer starten kann.
    when(etlAuthorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(false);

    assertThatThrownBy(() -> etlRunHistoryService.markFinished(7L))
        .isInstanceOf(AuthorizationException.class);

    verifyNoInteractions(runHistoryRepository);
  }

  @Test
  void a_run_that_does_not_exist_cannot_be_marked_as_finished() {
    when(etlAuthorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(true);
    when(runHistoryRepository.findById(7L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> etlRunHistoryService.markFinished(7L))
        .isInstanceOf(InvalidDataException.class)
        .satisfies(ex -> assertThat(errorCodesOf(ex)).containsExactly(ETL_RUN_NOT_FOUND));
  }

  @Test
  void a_run_that_is_no_longer_running_has_nothing_left_to_mark() {
    // Faengt den Doppelklick ebenso wie zwei gleichzeitige Klicks: der zweite sieht FAILED und
    // duerfte die Meldung des ersten nicht noch einmal ueberschreiben.
    var run = run(SUCCEEDED);
    when(etlAuthorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(true);
    when(runHistoryRepository.findById(7L)).thenReturn(Optional.of(run));

    assertThatThrownBy(() -> etlRunHistoryService.markFinished(7L))
        .isInstanceOf(BusinessRuleException.class)
        .satisfies(ex -> assertThat(errorCodesOf(ex)).containsExactly(ETL_RUN_NOT_RUNNING));

    assertThat(run.getStatus()).isEqualTo(SUCCEEDED);
    assertThat(run.getMessage()).isEqualTo("2 Definition(en) ausgeführt");
  }

  @Test
  void a_running_run_is_set_to_failed_with_an_end_and_a_note_about_who_ended_it() {
    var run = run(RUNNING);
    when(etlAuthorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(true);
    when(runHistoryRepository.findById(7L)).thenReturn(Optional.of(run));

    etlRunHistoryService.markFinished(7L);

    // FAILED und nicht SUCCEEDED: was der Lauf tatsaechlich geschafft hat, weiss niemand. Der
    // Vermerk unterscheidet die Zeile von einem Lauf, der von selbst scheiterte.
    assertThat(run.getStatus()).isEqualTo(FAILED);
    assertThat(run.getFinishedAt()).isNotNull();
    assertThat(run.getMessage())
        .startsWith("2 Definition(en) ausgeführt")
        .contains("Von Hand als beendet markiert.");
  }

  @Test
  void a_running_run_without_a_message_gets_the_note_alone() {
    var run = run(RUNNING);
    run.setMessage(null);
    when(etlAuthorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(true);
    when(runHistoryRepository.findById(7L)).thenReturn(Optional.of(run));

    etlRunHistoryService.markFinished(7L);

    assertThat(run.getMessage()).isEqualTo("Von Hand als beendet markiert.");
  }

  // --- Die Ausführungen eines Laufs (#1324) ---------------------------------------------------

  private static final PageRequest FIRST_PAGE = PageRequest.of(0, 50);

  @Test
  void the_executions_of_a_run_are_shown_only_for_definitions_the_login_may_read() {
    // Die Regel nennt die Definition 1 mit READ; fuer die 2 hat die Anmeldung keine (oder nur
    // EXECUTE). Ihre Ausfuehrungen tragen im Fehlerfall das SQL der Definition und bleiben verborgen.
    givenRun(FAILED, definition(1L, "readable", 2, 1), definition(2L, "foreign", 3, 0));
    when(etlAuthorization.isAuthorizedForDefinitionId(1L, READ)).thenReturn(true);
    when(etlAuthorization.isAuthorizedForDefinitionId(2L, READ)).thenReturn(false);
    var first = execution(10L, 1L);
    var second = execution(13L, 1L);
    when(executionHistoryRepository.findByRunInOrderOfExecution(7L, List.of(1L), false, FIRST_PAGE))
        .thenReturn(new PageImpl<>(List.of(first, second), FIRST_PAGE, 2));
    when(executionHistoryRepository.findIdsByRunInOrderOfExecution(7L)).thenReturn(List.of(10L, 11L, 12L, 13L, 14L));

    var detail = etlRunHistoryService.getRunDetail(7L, false, null, FIRST_PAGE);

    // die Nummer ist die im ganzen Lauf: die ausgeblendeten 11 und 12 zaehlen mit
    assertThat(detail.executions().getContent()).extracting(ExecutionInRun::position, ExecutionInRun::execution)
        .containsExactly(tuple(1, first), tuple(4, second));
    assertThat(detail.definitions()).extracting(ETLDefinitionInRun::etlName).containsExactly("readable");
    assertThat(detail.hiddenExecutions()).isEqualTo(3);
    assertThat(detail.readableExecutions()).isEqualTo(2);
    assertThat(detail.failedExecutions()).isEqualTo(1);
  }

  @Test
  void a_run_whose_executions_are_all_hidden_says_how_many_there_are() {
    // Sonst saehe er aus wie ein Lauf ohne Ausfuehrungen.
    givenRun(SUCCEEDED, definition(2L, "foreign", 1, 0), definition(3L, "other", 1, 0));
    when(etlAuthorization.isAuthorizedForDefinitionId(anyLong(), eq(READ))).thenReturn(false);

    var detail = etlRunHistoryService.getRunDetail(7L, false, null, FIRST_PAGE);

    assertThat(detail.executions()).isEmpty();
    assertThat(detail.definitions()).isEmpty();
    assertThat(detail.hiddenExecutions()).isEqualTo(2);
    verify(executionHistoryRepository, never()).findByRunInOrderOfExecution(anyLong(), any(), anyBoolean(), any());
  }

  @Test
  void the_filter_offers_the_readable_definitions_by_name() {
    givenRun(SUCCEEDED, definition(4L, "worked-hours", 3, 0), definition(5L, "Accounts", 3, 0));
    when(etlAuthorization.isAuthorizedForDefinitionId(anyLong(), eq(READ))).thenReturn(true);
    when(executionHistoryRepository.findByRunInOrderOfExecution(eq(7L), any(), eq(false), eq(FIRST_PAGE)))
        .thenReturn(Page.empty(FIRST_PAGE));

    var detail = etlRunHistoryService.getRunDetail(7L, false, null, FIRST_PAGE);

    assertThat(detail.definitions()).extracting(ETLDefinitionInRun::etlName).containsExactly("Accounts", "worked-hours");
  }

  @Test
  void filtered_by_a_definition_only_its_executions_are_read() {
    givenRun(FAILED, definition(1L, "first", 3, 0), definition(3L, "third", 3, 2));
    when(etlAuthorization.isAuthorizedForDefinitionId(anyLong(), eq(READ))).thenReturn(true);
    when(executionHistoryRepository.findByRunInOrderOfExecution(7L, List.of(3L), true, FIRST_PAGE))
        .thenReturn(Page.empty(FIRST_PAGE));

    var detail = etlRunHistoryService.getRunDetail(7L, true, 3L, FIRST_PAGE);

    verify(executionHistoryRepository).findByRunInOrderOfExecution(7L, List.of(3L), true, FIRST_PAGE);
    // die Auswahl des Filters bleibt vollstaendig, sonst liesse sich die Definition nicht wechseln
    assertThat(detail.definitions()).hasSize(2);
  }

  @Test
  void filtered_by_a_definition_the_login_may_not_read_nothing_is_shown() {
    // Die id kommt aus der Adresse, also aus fremder Hand.
    givenRun(FAILED, definition(1L, "readable", 3, 0), definition(2L, "foreign", 3, 1));
    when(etlAuthorization.isAuthorizedForDefinitionId(1L, READ)).thenReturn(true);
    when(etlAuthorization.isAuthorizedForDefinitionId(2L, READ)).thenReturn(false);

    var detail = etlRunHistoryService.getRunDetail(7L, false, 2L, FIRST_PAGE);

    assertThat(detail.executions()).isEmpty();
    verify(executionHistoryRepository, never()).findByRunInOrderOfExecution(anyLong(), any(), anyBoolean(), any());
  }

  @Test
  void a_page_behind_the_last_shows_the_last() {
    // etwa ein Link auf Seite 9, nachdem der Filter die Treffer auf zwei Seiten gebracht hat
    givenRun(FAILED, definition(1L, "readable", 60, 0));
    when(etlAuthorization.isAuthorizedForDefinitionId(1L, READ)).thenReturn(true);
    var ninth = PageRequest.of(8, 50);
    var second = PageRequest.of(1, 50);
    when(executionHistoryRepository.findByRunInOrderOfExecution(7L, List.of(1L), false, ninth))
        .thenReturn(new PageImpl<>(List.of(), ninth, 60));
    var last = execution(60L, 1L);
    when(executionHistoryRepository.findByRunInOrderOfExecution(7L, List.of(1L), false, second))
        .thenReturn(new PageImpl<>(List.of(last), second, 60));
    when(executionHistoryRepository.findIdsByRunInOrderOfExecution(7L)).thenReturn(List.of(59L, 60L));

    var detail = etlRunHistoryService.getRunDetail(7L, false, null, ninth);

    assertThat(detail.executions().getNumber()).isEqualTo(1);
    assertThat(detail.executions().getContent()).extracting(ExecutionInRun::execution).containsExactly(last);
  }

  @Test
  void a_run_without_executions_has_none_and_hides_none() {
    givenRun(Status.SKIPPED);

    var detail = etlRunHistoryService.getRunDetail(7L, false, null, FIRST_PAGE);

    assertThat(detail.run().getStatus()).isEqualTo(Status.SKIPPED);
    assertThat(detail.executions()).isEmpty();
    assertThat(detail.hiddenExecutions()).isZero();
    assertThat(detail.readableExecutions()).isZero();
  }

  @Test
  void without_a_rule_the_executions_are_refused_before_anything_is_read() {
    when(etlAuthorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(false);

    assertThatThrownBy(() -> etlRunHistoryService.getRunDetail(7L, false, null, FIRST_PAGE))
        .isInstanceOf(AuthorizationException.class);

    verifyNoInteractions(runHistoryRepository, executionHistoryRepository);
  }

  @Test
  void the_executions_of_a_run_that_does_not_exist_cannot_be_shown() {
    when(etlAuthorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(true);
    when(runHistoryRepository.findById(7L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> etlRunHistoryService.getRunDetail(7L, false, null, FIRST_PAGE))
        .isInstanceOf(InvalidDataException.class)
        .satisfies(ex -> assertThat(errorCodesOf(ex)).containsExactly(ETL_RUN_NOT_FOUND));
  }

  private void givenRun(Status status, ETLDefinitionInRun... definitions) {
    when(etlAuthorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(true);
    when(runHistoryRepository.findById(7L)).thenReturn(Optional.of(run(status)));
    when(executionHistoryRepository.countByDefinitionInRun(7L)).thenReturn(List.of(definitions));
  }

  private static ETLDefinitionInRun definition(long etlId, String etlName, long executions, long failed) {
    return new ETLDefinitionInRun(etlId, etlName, executions, failed);
  }

  private static ETLExecutionHistory execution(long id, long etlId) {
    return ETLExecutionHistory.builder()
        .id(id)
        .etlId(etlId)
        .etlName("etl-" + etlId)
        .executedAt(LocalDateTime.of(2026, 9, 20, 2, 1))
        .success(true)
        .message("Date Range: 2026-09-01 - 2026-09-30\n")
        .build();
  }

  @Test
  void a_run_in_progress_is_reported_as_such() {
    when(runHistoryRepository.findFirstByStatusOrderByStartedAtDesc(RUNNING))
        .thenReturn(Optional.of(run(RUNNING)));

    assertThat(etlRunHistoryService.isRunInProgress()).isTrue();
  }

  @Test
  void without_a_running_entry_nothing_is_in_progress() {
    when(runHistoryRepository.findFirstByStatusOrderByStartedAtDesc(RUNNING))
        .thenReturn(Optional.empty());

    assertThat(etlRunHistoryService.isRunInProgress()).isFalse();
  }

  private static ETLRunHistory run(Status status) {
    return ETLRunHistory.builder()
        .startedAt(LocalDateTime.of(2026, 9, 20, 2, 0))
        .finishedAt(status == RUNNING ? null : LocalDateTime.of(2026, 9, 20, 2, 4))
        .status(status)
        .triggeredBy(Trigger.MANUAL)
        .message("2 Definition(en) ausgeführt")
        .build();
  }

  private static List<ErrorCode> errorCodesOf(Throwable ex) {
    return ((ErrorCodeException) ex).getMessages().stream()
        .map(ServiceFeedbackMessage::getErrorCode)
        .toList();
  }

}
