package org.tb.common.filter;

import static jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static java.util.Optional.ofNullable;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Schreibt nach jeder Anfrage genau eine Zeile mit Methode, Pfad, Query, Status, Dauer und dem
 * angemeldeten Nutzer. Die Zugriffslogs des Hosting-Dienstes führen den Nutzer nicht, und ihr Format
 * lässt sich nicht erweitern (#1145).
 *
 * <p>Der Filter läuft vor der Spring-Security-Kette, weil nur dort der Status feststeht, wenn eine
 * Ausnahme durch die Kette läuft: Was Spring Security in 401 oder 403 übersetzt, ist an dieser Stelle
 * schon übersetzt, und was hier noch ankommt, beantwortet der Container mit 500. Den Nutzer kennt
 * der Filter hier nicht mehr, weil die Sicherheitskette ihren Kontext beim Verlassen abräumt.
 * {@link LoggingFilter} legt ihn deshalb als Request-Attribut ab.
 *
 * <p>Abschalten lässt sich die Zeile über den Level dieses Loggers.
 */
@Slf4j
@Component
@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1)
public class RequestLogFilter extends HttpFilter {

  private static final String HEALTH_CHECK_PATH = "/actuator/health";

  @Override
  protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    if (RequestPaths.isStaticResource(request) || HEALTH_CHECK_PATH.equals(request.getServletPath())) {
      super.doFilter(request, response, chain);
      return;
    }

    long start = System.nanoTime();
    boolean failed = false;
    try {
      super.doFilter(request, response, chain);
    } catch (Throwable t) {
      failed = true;
      throw t;
    } finally {
      int status = failed && !response.isCommitted() ? SC_INTERNAL_SERVER_ERROR : response.getStatus();
      logRequest(request, status, (System.nanoTime() - start) / 1_000_000);
    }
  }

  private void logRequest(HttpServletRequest request, int status, long durationMillis) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("request-uri", request.getRequestURI());
    fields.put("request-method", request.getMethod());
    fields.put("request-query-string", ofNullable(request.getQueryString()).orElse("<empty>"));
    fields.put("response-status", String.valueOf(status));
    fields.put("duration-ms", String.valueOf(durationMillis));
    if (request.getAttribute(LoggingFilter.MDC_DATA_ATTRIBUTE) instanceof Map<?, ?> mdcData) {
      mdcData.forEach((key, value) -> fields.put(String.valueOf(key), String.valueOf(value)));
    }

    fields.forEach(MDC::put);
    try {
      log.info("{} {} {} {} ms", request.getMethod(), request.getRequestURI(), status, durationMillis);
    } finally {
      fields.keySet().forEach(MDC::remove);
    }
  }

}
