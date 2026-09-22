package org.tb.employee.domain;

import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;

import lombok.Getter;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.tb.common.exception.ServiceFeedbackMessage;

/**
 * Warum der angemeldete Benutzer in dieser Anfrage nichts von der Anwendung zu sehen bekommt: kein
 * Mitarbeiterdatensatz zum Anmeldenamen, oder kein heute gültiger Vertrag.
 *
 * <p>Festgehalten wird das dort, wo die Bedingung auffällt — beim Wechsel des angemeldeten Benutzers
 * —, beantwortet wird es im {@code EmployeeAccessFilter}. Getrennt sind beide, weil ein
 * Ereignis-Listener über keinen HTTP-Status entscheiden kann: er läuft bei jeder Authentifizierung
 * und damit auch in der Weiterleitung auf {@code /error}. Eine dort geworfene Ausnahme nimmt die
 * Fehlerseite mit, und übrig bleibt die 500-Seite des Servlet-Containers (#1054).
 */
@Component
@Scope(value = SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class EmployeeAccessDenial {

  @Getter
  private ServiceFeedbackMessage reason;

  public void deny(ServiceFeedbackMessage reason) {
    this.reason = reason;
  }

  public boolean isDenied() {
    return reason != null;
  }

}
