package org.tb.etl.viewhelper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.etl.service.ETLRunHistoryService;

/**
 * Ob das Menü die ETL-Läufe anbietet (#573).
 *
 * <p>Die Frage ist dieselbe, die der Service beim Aufruf der Seite stellt — ein ausgeblendeter
 * Menüeintrag ist keine Autorisierung (#919), er soll nur nicht ins Leere führen.
 */
// Der Name steht ausgeschrieben da, weil Spring ihn aus einem Klassennamen mit zwei
// Grossbuchstaben am Anfang unveraendert uebernimmt: die Bohne hiesse sonst ETLAccessViewHelper,
// und `@etlAccessViewHelper` im Template fände nichts.
@Component("etlAccessViewHelper")
@RequiredArgsConstructor
public class ETLAccessViewHelper {

  private final AuthorizedUser authorizedUser;
  private final ETLRunHistoryService etlRunHistoryService;

  public boolean isRunHistoryVisible() {
    // Die Fehlerseite rendert dasselbe Layout, auch ohne Anmeldung. Ohne diese Frage vorweg liefe
    // die Rechteprüfung des Service dort in eine Ausnahme — innerhalb der Fehlerseite.
    return authorizedUser.isAuthenticated() && etlRunHistoryService.isRunHistoryVisible();
  }

}
