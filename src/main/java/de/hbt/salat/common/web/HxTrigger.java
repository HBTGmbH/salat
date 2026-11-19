package de.hbt.salat.common.web;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Der Header {@code HX-Trigger}, mit dem eine htmx-Antwort im Browser ein Ereignis auslöst (#1583).
 *
 * <p>Der Wert ist JSON und enthält nur ASCII: Tomcat schreibt Header als ISO-8859-1, und eine
 * Meldung mit Umlauten beantwortete der vorgeschaltete Proxy der Plattform mit 500, obwohl die
 * Anwendung 200 geschickt hatte. Alles außerhalb von ASCII steht deshalb als {@code \}{@code uXXXX}
 * im Wert, und htmx dekodiert es beim Parsen. Der Serialisierer maskiert auch Anführungszeichen und
 * Backslash, die der frühere Handbau verfälschte oder durchließ.
 */
public final class HxTrigger {

  private static final JsonMapper ASCII_JSON = JsonMapper.builder()
      .enable(JsonWriteFeature.ESCAPE_NON_ASCII)
      .build();

  private HxTrigger() {
  }

  /** Löst {@code showError} aus; der Toast der Seite zeigt die Meldung als Text. */
  public static void showError(HttpServletResponse response, String message) {
    response.setHeader("HX-Trigger", ASCII_JSON.writeValueAsString(Map.of("showError", message)));
  }
}
