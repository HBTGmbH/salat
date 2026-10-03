package de.hbt.salat.etl.auth;

import static de.hbt.salat.common.util.DateUtils.today;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.AccessLevel;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.etl.domain.ETLDefinition;

@Component
@RequiredArgsConstructor
public class ETLAuthorization {

  private static final String AUTH_CATEGORY = "ETL";

  private final AuthorizedUser authorizedUser;
  private final AuthService authService;

  public boolean isAuthorized(ETLDefinition etlDefinition, AccessLevel accessLevel) {
    if (authorizedUser.isManager()) return true;
    if (authorizedUser.isAdmin()) return true;
    // by id, not by name: a renamed definition keeps its rules (#1204)
    return authService.isAuthorized(AUTH_CATEGORY, today(), accessLevel, String.valueOf(etlDefinition.getId()));
  }

  /**
   * Ob überhaupt eine Regel der Kategorie {@code ETL} diese Zugriffsstufe gewährt — für alles, was
   * nicht an einer einzelnen Definition hängt, allen voran die Laufhistorie (#573): ein Lauf geht
   * über mehrere Definitionen und gehört keiner davon.
   */
  public boolean isAuthorizedForAnyETL(AccessLevel accessLevel) {
    if (authorizedUser.isManager()) return true;
    if (authorizedUser.isAdmin()) return true;
    return authService.isAuthorizedAnyObject(AUTH_CATEGORY, today(), accessLevel);
  }

}
