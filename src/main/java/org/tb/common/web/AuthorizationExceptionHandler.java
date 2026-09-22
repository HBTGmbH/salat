package org.tb.common.web;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.tb.common.exception.ErrorCode.AA_REQUIRED;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.common.filter.RequestPaths;
import org.tb.common.viewhelper.ErrorCodeViewHelper;

/**
 * Die Antwort auf eine abgewiesene Berechtigung: {@code 403}, und auf der Seite steht, welche
 * gefehlt hat.
 *
 * <p>Ohne diese Stelle beantwortet die Anwendung sie mit {@code 500}. {@code AuthorizationException}
 * ist eine gewöhnliche {@code RuntimeException} — Spring Security sieht sie nicht, denn sie kommt
 * nicht aus deren Rechteprüfung, sondern aus {@code AuthorizationAspect}. Sie läuft also unbehandelt
 * aus dem {@code DispatcherServlet} heraus und landet als Serverfehler auf der Fehlerseite,
 * mitsamt Stacktrace. Solange die Controller {@code @PreAuthorize} trugen, fiel das nicht auf: die
 * Prüfung lag bei Spring Security und deren {@code AccessDeniedException} wird beantwortet. Seit
 * #926 liegt sie beim Aspekt, und damit ist der Statuscode diese Stelle wert. Die drei
 * Budget-Lesecontroller trugen den Fehler seit #919 unbemerkt.
 *
 * <p>Der Weg über {@code sendError} ist derselbe wie bei {@code EmployeeAccessFilter} (#1054): der
 * Servlet-Container leitet auf {@code /error} weiter, und die Fehlerseite der Anwendung antwortet
 * statt der des Containers. Die aufgelöste Meldung wird mitgegeben, kommt dort derzeit aber nicht
 * an — {@code jakarta.servlet.error.message} ist in der Weiterleitung leer, und zwar für den Filter
 * aus #1054 genauso. Das ist ein Mangel der Fehlerseite, kein Grund, den Grund hier wegzulassen.
 *
 * <p>Für die zustandslosen Ketten ({@code /api}, {@code /rest}) wäre eine HTML-Seite die falsche
 * Antwort an ein Skript; dort steht ein {@code ProblemDetail}, wieder wie in
 * {@code EmployeeAccessFilter}.
 *
 * <p>Der Advice greift zuletzt ({@link Ordered#LOWEST_PRECEDENCE}), damit ein Endpunkt mit eigener
 * Fehlerbehandlung seine behält — {@code ReportRestExceptionHandler} unterscheidet feiner und
 * antwortet auf eine fehlende Anmeldung mit {@code 401}.
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class AuthorizationExceptionHandler {

  private static final JacksonJsonHttpMessageConverter JSON = new JacksonJsonHttpMessageConverter();

  private final ErrorCodeViewHelper errorCodeViewHelper;

  /**
   * {@code void} plus {@code HttpServletResponse}: damit gilt die Anfrage als beantwortet und
   * Spring MVC sucht keine Sicht mehr. Geschrieben ist die Antwort an dieser Stelle bereits.
   */
  @ExceptionHandler(AuthorizationException.class)
  public void handle(AuthorizationException exception, HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    var reason = exception.getMessages().stream().findFirst().orElse(null);
    var status = statusOf(reason);

    if (RequestPaths.isStateless(request)) {
      writeProblemDetail(response, status, reason);
      return;
    }
    response.sendError(status.value(), reason == null ? status.getReasonPhrase()
        : errorCodeViewHelper.toViewMessage(reason).resolved());
  }

  /**
   * Eine fehlende Anmeldung ist {@code 401} und keine fehlende Berechtigung. In der Oberfläche kommt
   * der Fall nicht vor — die Filterketten verlangen für jede Anfrage eine Anmeldung —, wohl aber in
   * einem Aufruf ohne Sicherheitskontext.
   */
  private static HttpStatus statusOf(ServiceFeedbackMessage reason) {
    return reason != null && reason.getErrorCode() == AA_REQUIRED ? UNAUTHORIZED : FORBIDDEN;
  }

  private void writeProblemDetail(HttpServletResponse response, HttpStatus status, ServiceFeedbackMessage reason)
      throws IOException {
    var problemDetail = ProblemDetail.forStatus(status);
    if (reason != null) {
      problemDetail.setTitle(reason.getErrorCode().getCode());
      problemDetail.setDetail(reason.getErrorCode().getMessage());
    }
    response.setStatus(status.value());
    JSON.write(problemDetail, APPLICATION_PROBLEM_JSON, new ServletServerHttpResponse(response));
  }

}
