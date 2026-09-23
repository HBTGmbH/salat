package org.tb.etl.service;

import static org.tb.common.exception.ErrorCode.AA_NEEDS_MANAGER;
import static org.tb.etl.domain.ETLRunHistory.Status.SUCCEEDED;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.exception.AuthorizationException;
import org.tb.etl.domain.ETLRunHistory;
import org.tb.etl.persistence.ETLRunHistoryRepository;

/**
 * Liest die Laufhistorie des ETL (#573) für die Anzeige.
 *
 * <p>Management only, wie die JIRA-Replikationen daneben: die Meldung eines Laufs enthält das
 * abgesetzte SQL und damit den inneren Aufbau der Auswertungen.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Authorized(requiresManager = true)
public class ETLRunHistoryService {

  private final ETLRunHistoryRepository runHistoryRepository;
  private final AuthorizedUser authorizedUser;

  /**
   * Die jüngsten Läufe, neueste zuerst.
   *
   * @param failedOnly nur die Läufe, die nicht sauber zu Ende kamen — {@code FAILED} und
   *     {@code RUNNING}. Ein Lauf, der abgestürzt ist, bleibt für immer auf {@code RUNNING} stehen;
   *     ihn hier herauszufiltern hieße, genau den Fall zu verstecken, für den es die Tabelle gibt.
   */
  public List<ETLRunHistory> getLatestRuns(int limit, boolean failedOnly) {
    checkManager();
    var page = PageRequest.of(0, limit);
    return failedOnly
        ? runHistoryRepository.findByStatusNotOrderByStartedAtDesc(SUCCEEDED, page)
        : runHistoryRepository.findByOrderByStartedAtDesc(page);
  }

  private void checkManager() {
    if (!authorizedUser.isManager()) {
      throw new AuthorizationException(AA_NEEDS_MANAGER);
    }
  }

}
