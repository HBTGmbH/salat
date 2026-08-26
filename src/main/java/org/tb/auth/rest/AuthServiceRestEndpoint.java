package org.tb.auth.rest;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.service.AuthService;
import org.tb.common.exception.AuthorizationException;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/auth", produces = APPLICATION_JSON_VALUE)
@Tag(name = "auth-service", description = "API für administrative Operationen der Berechtigungsprüfung")
public class AuthServiceRestEndpoint {

  private final AuthService authService;
  private final AuthorizedUser authorizedUser;

  @PostMapping(path = "/cache/clear")
  @ResponseStatus(OK)
  @Operation(summary = "Leert den Autorisierungs-Cache",
      description = """
          Verwirft die zwischengespeicherten Autorisierungsregeln, sodass die nächste Berechtigungsprüfung sie \
          erneut aus der Datenbank liest. Eine direkt in der Datenbank gepflegte Regel greift damit sofort, \
          ohne das Ablaufen von salat.auth-service.cache-expiry abzuwarten.

          Der Cache liegt in der jeweiligen JVM. Läuft die Anwendung mit mehr als einer Instanz, leert ein \
          Aufruf nur die Instanz, die den Request bedient.""",
      responses = {
          @ApiResponse(responseCode = "200", description = "Der Cache wurde geleert."),
          @ApiResponse(responseCode = "401", description = "Nicht authentifiziert"),
          @ApiResponse(responseCode = "403", description = "Keine Berechtigung zum Leeren des Autorisierungs-Caches")
      })
  public void clearCache() {
    if (!authorizedUser.isAuthenticated()) {
      throw new ResponseStatusException(UNAUTHORIZED);
    }

    try {
      authService.clearCache();
    } catch (AuthorizationException e) {
      throw new ResponseStatusException(FORBIDDEN, "Could not clear the authorization cache. " + e);
    }
  }

}
