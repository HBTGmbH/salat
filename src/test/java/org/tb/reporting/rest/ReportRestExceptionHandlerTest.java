package org.tb.reporting.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.tb.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static org.tb.common.exception.ErrorCode.AA_REQUIRED;
import static org.tb.common.exception.ErrorCode.RP_REPORT_EXECUTION_FAILED;
import static org.tb.common.exception.ErrorCode.RP_REPORT_NAME_AMBIGUOUS;
import static org.tb.common.exception.ErrorCode.RP_REPORT_NOT_FOUND;
import static org.tb.common.exception.ErrorCode.RP_REPORT_PARAMETERS_MISSING;
import static org.tb.common.exception.ErrorCode.RP_REPORT_PARAMETER_INVALID;

import org.junit.jupiter.api.Test;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.exception.InvalidDataException;

class ReportRestExceptionHandlerTest {

  private final ReportRestExceptionHandler handler = new ReportRestExceptionHandler();

  @Test
  void anUnknownReportIsNotFound() {
    assertThat(handler.handle(new InvalidDataException(RP_REPORT_NOT_FOUND, "Fehlt")).getStatusCode())
        .isEqualTo(NOT_FOUND);
  }

  @Test
  void anAmbiguousNameIsAConflict() {
    assertThat(handler.handle(new BusinessRuleException(RP_REPORT_NAME_AMBIGUOUS, "Doppelt", 2)).getStatusCode())
        .isEqualTo(CONFLICT);
  }

  @Test
  void aParameterProblemIsABadRequest() {
    assertThat(handler.handle(new InvalidDataException(RP_REPORT_PARAMETERS_MISSING, "jahr")).getStatusCode())
        .isEqualTo(BAD_REQUEST);
    assertThat(handler.handle(new InvalidDataException(RP_REPORT_PARAMETER_INVALID, "von", "x")).getStatusCode())
        .isEqualTo(BAD_REQUEST);
  }

  @Test
  void aBrokenReportIsAServerError() {
    assertThat(handler.handle(new BusinessRuleException(RP_REPORT_EXECUTION_FAILED, "Kaputt")).getStatusCode())
        .isEqualTo(INTERNAL_SERVER_ERROR);
  }

  @Test
  void missingAuthenticationIsUnauthorizedAndMissingPermissionIsForbidden() {
    assertThat(handler.handle(new AuthorizationException(AA_REQUIRED)).getStatusCode()).isEqualTo(UNAUTHORIZED);
    assertThat(handler.handle(new AuthorizationException(AA_NOT_ATHORIZED)).getStatusCode()).isEqualTo(FORBIDDEN);
  }

  @Test
  void anUnmappedCodeIsAServerErrorRatherThanAWrongPromise() {
    assertThat(handler.handle(new BusinessRuleException(ErrorCode.XX_DATA_MISSING)).getStatusCode())
        .isEqualTo(INTERNAL_SERVER_ERROR);
  }

  @Test
  void theBodyNamesTheCodeAndTheArgumentsACallerNeeds() {
    var response = handler.handle(new InvalidDataException(RP_REPORT_PARAMETERS_MISSING, "jahr, monat"));

    var problemDetail = response.getBody();
    assertThat(problemDetail).isNotNull();
    assertThat(problemDetail.getTitle()).isEqualTo("RP-0003");
    assertThat(problemDetail.getDetail()).isEqualTo(RP_REPORT_PARAMETERS_MISSING.getMessage());
    assertThat(problemDetail.getProperties()).containsEntry("arguments", java.util.List.of("jahr, monat"));
  }

  @Test
  void theAnswerIsAProblemDocumentWhateverTheCallerAccepts() {
    // without an explicit content type a caller asking for text/csv would get a 406 instead of the error
    var response = handler.handle(new InvalidDataException(RP_REPORT_NOT_FOUND, "Fehlt"));

    assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);
  }

  @Test
  void anExceptionWithoutAMessageIsStillAnswered() {
    var response = handler.handle(new ErrorCodeException(java.util.List.of()));

    assertThat(response.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).isNotNull();
  }

}
