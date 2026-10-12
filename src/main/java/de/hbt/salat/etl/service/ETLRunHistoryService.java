package de.hbt.salat.etl.service;

import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsLast;
import static de.hbt.salat.auth.domain.AccessLevel.EXECUTE;
import static de.hbt.salat.auth.domain.AccessLevel.READ;
import static de.hbt.salat.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static de.hbt.salat.common.exception.ErrorCode.ETL_RUN_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.ETL_RUN_NOT_RUNNING;
import static de.hbt.salat.etl.domain.ETLRunHistory.MESSAGE_MAX_LENGTH;
import static de.hbt.salat.etl.domain.ETLRunHistory.Status.FAILED;
import static de.hbt.salat.etl.domain.ETLRunHistory.Status.RUNNING;
import static de.hbt.salat.etl.domain.ETLRunHistory.Status.SUCCEEDED;

import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.util.DateTimeUtils;
import de.hbt.salat.etl.auth.ETLAuthorization;
import de.hbt.salat.etl.domain.ETLDefinitionInRun;
import de.hbt.salat.etl.domain.ETLExecutionHistory;
import de.hbt.salat.etl.domain.ETLRunDetail;
import de.hbt.salat.etl.domain.ETLRunDetail.ExecutionInRun;
import de.hbt.salat.etl.domain.ETLRunHistory;
import de.hbt.salat.etl.persistence.ETLExecutionHistoryRepository;
import de.hbt.salat.etl.persistence.ETLRunHistoryRepository;

/**
 * Liest die Laufhistorie des ETL (#573) für die Anzeige.
 *
 * <p>Wer einen ETL ausführen darf, darf auch sehen, wie die Läufe ausgegangen sind: neben
 * Geschäftsführung und Administration kommt hier durch, wer eine Regel der Kategorie {@code ETL} mit
 * {@code EXECUTE} hat. Deshalb kein {@code @Authorized(requiresManager = true)} am Controller — ein
 * Entweder-oder ist eine Laufzeitprüfung im Service, keine Annotation (→ AGENTS.md).
 *
 * <p>Die Regel hängt sonst an einer einzelnen Definition; ein Lauf geht über mehrere und gehört
 * keiner davon. Gezeigt werden deshalb alle Läufe, auch wenn die Regel nur eine Definition nennt —
 * und die Meldung eines Laufs nennt die Namen der Definitionen, die gelaufen sind.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Authorized
public class ETLRunHistoryService {

  private final ETLRunHistoryRepository runHistoryRepository;
  private final ETLExecutionHistoryRepository executionHistoryRepository;
  private final ETLAuthorization etlAuthorization;

  /**
   * Die jüngsten Läufe, neueste zuerst.
   *
   * @param failedOnly nur die Läufe, die nicht sauber zu Ende kamen — {@code FAILED} und
   *     {@code RUNNING}. Ein Lauf, der abgestürzt ist, bleibt für immer auf {@code RUNNING} stehen;
   *     ihn hier herauszufiltern hieße, genau den Fall zu verstecken, für den es die Tabelle gibt.
   */
  public List<ETLRunHistory> getLatestRuns(int limit, boolean failedOnly) {
    checkAuthorized();
    var page = PageRequest.of(0, limit);
    return failedOnly
        ? runHistoryRepository.findByStatusNotOrderByStartedAtDesc(SUCCEEDED, page)
        : runHistoryRepository.findByOrderByStartedAtDesc(page);
  }

  /**
   * Ein Lauf mit einer Seite seiner Ausführungen (#1324), in der Reihenfolge, in der sie liefen.
   *
   * <p>Zwei Stufen: den Lauf sieht, wer die Liste sieht — er gehört keiner einzelnen Definition.
   * Eine Ausführung dagegen gehört genau einer, und ihre Meldung nennt im Fehlerfall das SQL dieser
   * Definition. Sie wird deshalb nur gezeigt, wenn die Person die Definition lesen darf
   * ({@code READ}); eine Regel mit {@code EXECUTE} allein öffnet die Liste, nicht das SQL.
   *
   * <p>Das Recht wird je Definition gefragt, nicht je Ausführung, und die Abfrage der Seite nennt
   * nur die lesbaren. So stimmen Seiten und Gesamtzahl, wie viele Ausführungen auch ausgeblendet
   * sind.
   *
   * @param failedOnly nur die fehlgeschlagenen Ausführungen
   * @param definitionId nur die Ausführungen dieser Definition, {@code null} für alle. Eine
   *     Definition, die die Person nicht lesen darf, ergibt keine.
   * @param pageable die gewünschte Seite. Eine Seite hinter der letzten, etwa aus einem Link von
   *     vor einem Filterwechsel, wird zur letzten.
   * @throws AuthorizationException ohne ETL-Recht — wie die Liste
   * @throws InvalidDataException wenn es den Lauf nicht gibt
   */
  public ETLRunDetail getRunDetail(long runId, boolean failedOnly, Long definitionId, Pageable pageable) {
    checkAuthorized();
    var run = runHistoryRepository.findById(runId)
        .orElseThrow(() -> new InvalidDataException(ETL_RUN_NOT_FOUND));
    var definitions = executionHistoryRepository.countByDefinitionInRun(runId);
    var readable = definitions.stream()
        .filter(definition -> isReadable(definition.etlId()))
        .sorted(comparing(ETLDefinitionInRun::etlName, nullsLast(String.CASE_INSENSITIVE_ORDER)))
        .toList();
    var hidden = countOf(definitions) - countOf(readable);
    var shownIds = readable.stream()
        .map(ETLDefinitionInRun::etlId)
        .filter(id -> definitionId == null || id.equals(definitionId))
        .toList();
    var page = pageOf(runId, shownIds, failedOnly, pageable);
    var idsInRun = page.isEmpty() ? List.<Long>of() : executionHistoryRepository.findIdsByRunInOrderOfExecution(runId);
    return new ETLRunDetail(run,
        page.map(execution -> new ExecutionInRun(positionOf(execution, idsInRun), execution)),
        readable, hidden);
  }

  private Page<ETLExecutionHistory> pageOf(long runId, List<Long> etlIds, boolean failedOnly, Pageable pageable) {
    if (etlIds.isEmpty()) {
      return Page.empty(pageable);
    }
    var page = executionHistoryRepository.findByRunInOrderOfExecution(runId, etlIds, failedOnly, pageable);
    var behindTheLast = page.isEmpty() && page.getTotalPages() > 0;
    return behindTheLast
        ? executionHistoryRepository.findByRunInOrderOfExecution(runId, etlIds, failedOnly,
            pageable.withPage(page.getTotalPages() - 1))
        : page;
  }

  /** Die Nummer im ganzen Lauf, ab 1 — auch die ausgeblendeten Ausführungen zählen mit. */
  private static int positionOf(ETLExecutionHistory execution, List<Long> idsInRun) {
    return Collections.binarySearch(idsInRun, execution.getId()) + 1;
  }

  private static long countOf(List<ETLDefinitionInRun> definitions) {
    return definitions.stream().mapToLong(ETLDefinitionInRun::executions).sum();
  }

  private boolean isReadable(long etlId) {
    return etlAuthorization.isAuthorizedForDefinitionId(etlId, READ);
  }

  /** Ob die Anzeige für die anfragende Person überhaupt offen ist — auch die Frage des Menüs. */
  public boolean isRunHistoryVisible() {
    return etlAuthorization.isAuthorizedForAnyETL(EXECUTE);
  }

  /**
   * Ob gerade ein Lauf läuft — für die Anzeige, nicht für die Sperre (#1071).
   *
   * <p>Die Sperre ist {@code ETLService.startRun}: sie prüft und schreibt in einem Abschnitt. Was
   * hier beantwortet wird, ist die Frage des Formulars, ob es den Knopf überhaupt anbieten soll.
   * Zwischen dieser Frage und dem Klick kann sich der Zustand ändern; deshalb entscheidet sie nichts.
   */
  public boolean isRunInProgress() {
    return runHistoryRepository.findFirstByStatusOrderByStartedAtDesc(RUNNING).isPresent();
  }

  /**
   * Setzt einen Lauf von Hand auf beendet (#1071).
   *
   * <p>Ein Lauf, dessen Prozess gestorben ist, bleibt für immer auf {@code RUNNING} stehen — das ist
   * der Sinn der Spalte (#573), blockiert aber jeden weiteren Start. Das ist der Weg daran vorbei.
   * Bewusst kein Zurücksetzen beim Hochfahren der Anwendung: das griffe bei mehreren Instanzen
   * daneben und setzte einen laufenden Lauf mit zurück. Und bewusst keine Altersschwelle: jede Zahl
   * wäre geraten, und sie beendete den Lauf ohne Zutun genau dann, wenn er ungewöhnlich lange
   * braucht.
   *
   * <p>Der Lauf wird dadurch nicht angehalten — die Anwendung kann einen Thread, der in einer
   * SQL-Anweisung steht, nicht abbrechen. Der Bestätigungstext sagt das.
   */
  @Transactional
  public void markFinished(long runId) {
    checkAuthorized();
    var run = runHistoryRepository.findById(runId)
        .orElseThrow(() -> new InvalidDataException(ETL_RUN_NOT_FOUND));
    if (run.getStatus() != RUNNING) {
      // Fängt den Doppelklick ebenso wie zwei gleichzeitige Klicks: der zweite sieht FAILED.
      throw new BusinessRuleException(ETL_RUN_NOT_RUNNING);
    }
    run.setStatus(FAILED);
    run.setFinishedAt(DateTimeUtils.now());
    run.setMessage(withNote(run.getMessage()));
  }

  private static String withNote(String message) {
    var note = "Von Hand als beendet markiert.";
    var combined = message == null || message.isBlank() ? note : message + "\n" + note;
    return combined.length() > MESSAGE_MAX_LENGTH
        ? combined.substring(0, MESSAGE_MAX_LENGTH)
        : combined;
  }

  private void checkAuthorized() {
    if (!isRunHistoryVisible()) {
      throw new AuthorizationException(AA_NOT_ATHORIZED);
    }
  }

}
