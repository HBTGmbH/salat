package org.tb.etl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.tb.auth.domain.AccessLevel.EXECUTE;
import static org.tb.common.exception.ErrorCode.ETL_RUN_NOT_FOUND;
import static org.tb.common.exception.ErrorCode.ETL_RUN_NOT_RUNNING;
import static org.tb.etl.domain.ETLRunHistory.Status.FAILED;
import static org.tb.etl.domain.ETLRunHistory.Status.RUNNING;
import static org.tb.etl.domain.ETLRunHistory.Status.SUCCEEDED;

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
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.etl.auth.ETLAuthorization;
import org.tb.etl.domain.ETLRunHistory;
import org.tb.etl.domain.ETLRunHistory.Status;
import org.tb.etl.domain.ETLRunHistory.Trigger;
import org.tb.etl.persistence.ETLRunHistoryRepository;

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
