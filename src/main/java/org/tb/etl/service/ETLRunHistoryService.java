package org.tb.etl.service;

import static org.tb.auth.domain.AccessLevel.EXECUTE;
import static org.tb.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static org.tb.common.exception.ErrorCode.ETL_RUN_NOT_FOUND;
import static org.tb.common.exception.ErrorCode.ETL_RUN_NOT_RUNNING;
import static org.tb.etl.domain.ETLRunHistory.MESSAGE_MAX_LENGTH;
import static org.tb.etl.domain.ETLRunHistory.Status.FAILED;
import static org.tb.etl.domain.ETLRunHistory.Status.RUNNING;
import static org.tb.etl.domain.ETLRunHistory.Status.SUCCEEDED;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.DateTimeUtils;
import org.tb.etl.auth.ETLAuthorization;
import org.tb.etl.domain.ETLRunHistory;
import org.tb.etl.persistence.ETLRunHistoryRepository;

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
