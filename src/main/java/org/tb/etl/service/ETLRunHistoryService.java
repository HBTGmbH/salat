package org.tb.etl.service;

import static org.tb.auth.domain.AccessLevel.EXECUTE;
import static org.tb.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static org.tb.etl.domain.ETLRunHistory.Status.SUCCEEDED;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.common.exception.AuthorizationException;
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

  private void checkAuthorized() {
    if (!isRunHistoryVisible()) {
      throw new AuthorizationException(AA_NOT_ATHORIZED);
    }
  }

}
