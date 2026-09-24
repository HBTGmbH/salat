package org.tb.etl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tb.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static org.tb.common.exception.ErrorCode.ETL_RUN_EXECUTOR_BUSY;
import static org.tb.etl.domain.ETLRunHistory.Status.FAILED;
import static org.tb.etl.domain.ETLRunHistory.Status.RUNNING;
import static org.tb.etl.domain.ETLRunHistory.Trigger.MANUAL;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.LocalDateRange;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.etl.domain.ETLRunHistory;
import org.tb.etl.persistence.ETLRunHistoryRepository;

/**
 * Die Arbeitsteilung zwischen Anfrage- und Hintergrundthread beim Anstoßen eines Laufs (#1071).
 *
 * <p>Alles, was die anfragende Person betrifft — Zeitraum, Berechtigung, welche Definitionen laufen
 * und ob überhaupt gestartet werden darf —, fällt im Thread der Anfrage. Im Hintergrund arbeitet der
 * Lauf als {@code SYSTEM} und käme durch jede Prüfung; eine Prüfung dort sähe aus wie eine und wäre
 * keine.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ETLRunLauncherTest {

  private static final LocalDateRange ONE_MONTH =
      new LocalDateRange(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 20));

  @Mock
  private ETLService etlService;

  @Mock
  private ETLRunHistoryRepository runHistoryRepository;

  @Mock
  private ObjectProvider<AuthorizedUser> authorizedUserProvider;

  @Mock
  private AuthorizedUser authorizedUser;

  @Mock
  private ThreadPoolTaskExecutor etlTaskExecutor;

  @InjectMocks
  private ETLRunLauncher etlRunLauncher;

  @Test
  void the_permission_and_the_order_are_settled_before_the_run_is_even_opened() {
    when(etlService.resolveManualRun(ONE_MONTH, "report")).thenReturn(List.of("base", "report"));
    when(etlService.startRun(ONE_MONTH, MANUAL)).thenReturn(openedRun());

    var run = etlRunLauncher.startManualRun(ONE_MONTH, "report");

    // Die Reihenfolge ist die Aussage: waere die Sperre zuerst gezogen, hinterliesse eine
    // abgewiesene Berechtigung eine RUNNING-Zeile, die niemand mehr auffuellt.
    var order = inOrder(etlService, etlTaskExecutor);
    order.verify(etlService).resolveManualRun(ONE_MONTH, "report");
    order.verify(etlService).startRun(ONE_MONTH, MANUAL);
    order.verify(etlTaskExecutor).execute(any());
    assertThat(run.getStatus()).isEqualTo(RUNNING);
  }

  @Test
  void a_refused_permission_leaves_no_opened_run_behind() {
    when(etlService.resolveManualRun(ONE_MONTH, "report"))
        .thenThrow(new AuthorizationException(AA_NOT_ATHORIZED));

    assertThatThrownBy(() -> etlRunLauncher.startManualRun(ONE_MONTH, "report"))
        .isInstanceOf(AuthorizationException.class);

    verify(etlService, never()).startRun(any(), any());
    verify(etlTaskExecutor, never()).execute(any());
  }

  @Test
  void the_background_thread_works_as_the_system_and_continues_the_opened_run() {
    var run = openedRun();
    when(etlService.resolveManualRun(ONE_MONTH, null)).thenReturn(List.of("base", "report"));
    when(etlService.startRun(ONE_MONTH, MANUAL)).thenReturn(run);
    when(authorizedUserProvider.getObject()).thenReturn(authorizedUser);
    runSubmittedTaskImmediately();

    etlRunLauncher.startManualRun(ONE_MONTH, null);

    // Ohne HTTP-Anfrage gibt es keinen Request-Scope; ohne initForJob haette der Lauf im
    // Hintergrund keine gueltige Anmeldung (→ ADR-0006).
    verify(authorizedUser).initForJob();
    // Weitergereicht wird die Id, nicht die Entitaet. Den Schutz davor, eine zwischenzeitlich von
    // Hand beendete Zeile zu ueberschreiben, leistet allerdings erst das erneute Lesen in
    // ETLService.finishRun — hier wird nur festgehalten, dass der Hintergrundlauf die Zeile selbst
    // holt, statt einen Abzug ueber die ganze Laufzeit mitzuschleppen.
    verify(etlService).continueRun(eq(run.getId()), eq(ONE_MONTH), eq(List.of("base", "report")));
  }

  @Test
  void a_run_that_never_reached_the_executor_does_not_look_like_one_that_is_still_going() {
    var run = openedRun();
    when(etlService.resolveManualRun(ONE_MONTH, null)).thenReturn(List.of("base"));
    when(etlService.startRun(ONE_MONTH, MANUAL)).thenReturn(run);
    doThrow(new TaskRejectedException("no free thread")).when(etlTaskExecutor).execute(any());

    // Der Weg hierher ist vorgesehen (→ ADR-0028): wer einen laufenden Lauf von Hand als beendet
    // markiert, gibt die Sperre frei, haelt den Thread aber nicht an. Was dann abgewiesen wird,
    // muss als Meldung ankommen — eine durchgereichte TaskRejectedException faengt niemand, sie
    // waere eine 500-Fehlerseite.
    assertThatThrownBy(() -> etlRunLauncher.startManualRun(ONE_MONTH, null))
        .isInstanceOf(BusinessRuleException.class)
        .satisfies(ex -> assertThat(((BusinessRuleException) ex).getMessages().getFirst().getErrorCode())
            .isEqualTo(ETL_RUN_EXECUTOR_BUSY));

    // Bliebe die Zeile auf RUNNING stehen, blockierte sie jeden weiteren Start — ein Lauf, der nie
    // begann, darf nicht wie einer aussehen, der noch laeuft.
    assertThat(run.getStatus()).isEqualTo(FAILED);
    assertThat(run.getFinishedAt()).isNotNull();
    verify(runHistoryRepository).save(run);
  }

  private void runSubmittedTaskImmediately() {
    doAnswer(invocation -> {
      invocation.getArgument(0, Runnable.class).run();
      return null;
    }).when(etlTaskExecutor).execute(any());
  }

  private static ETLRunHistory openedRun() {
    var run = ETLRunHistory.builder()
        .startedAt(LocalDateTime.of(2026, 9, 24, 14, 3, 11))
        .status(RUNNING)
        .triggeredBy(MANUAL)
        .dateFrom(ONE_MONTH.getFrom())
        .dateUntil(ONE_MONTH.getUntil())
        .build();
    ReflectionTestUtils.setField(run, "id", 4711L);
    return run;
  }

}
