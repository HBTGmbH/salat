package org.tb.reporting.rest;

import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.exception.ServiceFeedbackMessage;

/**
 * Fehler dieses Endpunkts als {@code application/problem+json} (RFC 9457).
 *
 * <p>Ohne diese Stelle beantwortet die Anwendung auch einen API-Aufruf mit ihrer Fehlerseite: 27 KB
 * HTML, in denen weder der Fehlercode noch der fehlende Parametername steht. Der Statuscode allein
 * sagt einem Skript nicht, <em>welcher</em> Parameter fehlt.
 *
 * <p>Der Advice ist absichtlich auf {@link ReportRestEndpoint} begrenzt: die Fehlerseite der
 * Oberfläche bleibt, wie sie ist, und {@code spring.mvc.problemdetails.enabled} hätte sie für jeden
 * Controller ersetzt.
 *
 * <p>Der Content-Type steht ausdrücklich in der Antwort. Sonst käme ein Aufrufer mit
 * {@code Accept: text/csv} im Fehlerfall zu einem 406, weil sich für ein Problemdokument kein
 * CSV-Konverter findet.
 */
@Slf4j
@Order(HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ReportRestEndpoint.class)
public class ReportRestExceptionHandler {

  @ExceptionHandler(ErrorCodeException.class)
  public ResponseEntity<ProblemDetail> handle(ErrorCodeException e) {
    var message = e.getMessages().stream().findFirst().orElse(null);
    var status = statusOf(message);
    if (status.is5xxServerError()) {
      log.error("Report request failed", e);
    }

    var problemDetail = ProblemDetail.forStatus(status);
    if (message != null) {
      problemDetail.setTitle(message.getErrorCode().getCode());
      problemDetail.setDetail(message.getErrorCode().getMessage());
      if (!message.getArguments().isEmpty()) {
        // Hier stehen die Angaben, die der Aufrufer braucht: der gesuchte Name, die fehlenden
        // Parameter, der unbrauchbare Wert.
        problemDetail.setProperty("arguments", message.getArguments().stream().map(String::valueOf).toList());
      }
    }

    return ResponseEntity.status(status).contentType(APPLICATION_PROBLEM_JSON).body(problemDetail);
  }

  private static HttpStatus statusOf(ServiceFeedbackMessage message) {
    if (message == null) {
      return INTERNAL_SERVER_ERROR;
    }
    return switch (message.getErrorCode()) {
      case RP_REPORT_NOT_FOUND -> NOT_FOUND;
      case RP_REPORT_NAME_AMBIGUOUS -> CONFLICT;
      case RP_REPORT_PARAMETERS_MISSING, RP_REPORT_PARAMETER_INVALID -> BAD_REQUEST;
      case RP_REPORT_EXECUTION_FAILED -> INTERNAL_SERVER_ERROR;
      case AA_REQUIRED -> UNAUTHORIZED;
      case AA_NOT_ATHORIZED, AA_NEEDS_UNRESTRICTED, AA_NEEDS_BACKOFFICE,
           AA_NEEDS_PEOPLE_LEAD, AA_NEEDS_MANAGER, AA_NEEDS_ADMIN -> FORBIDDEN;
      default -> INTERNAL_SERVER_ERROR;
    };
  }

}
