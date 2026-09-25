package org.tb.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.UncategorizedSQLException;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ReportSqlErrorsTest {

  @Test
  void a_syntax_error_keeps_sql_state_and_error_code() {
    var sqlException = new SQLException("Table not found", "42S02", 42102);

    var errorInfo = ReportSqlErrors.describe(
        new BadSqlGrammarException("StatementCallback", "select * from nowhere", sqlException));

    assertThat(errorInfo.getErrorClass()).isEqualTo("BadSqlGrammarException");
    assertThat(errorInfo.getErrorMessage()).isEqualTo("Table not found");
    assertThat(errorInfo.getSqlState()).isEqualTo("42S02");
    assertThat(errorInfo.getErrorCode()).isEqualTo(42102);
  }

  @Test
  void an_error_that_only_shows_while_executing_is_described_just_as_well() {
    var sqlException = new SQLException("Division by zero", "22012", 22012);

    var errorInfo = ReportSqlErrors.describe(
        new UncategorizedSQLException("PreparedStatementCallback", "select 1 / n from t", sqlException));

    assertThat(errorInfo.getErrorClass()).isEqualTo("UncategorizedSQLException");
    assertThat(errorInfo.getErrorMessage()).isEqualTo("Division by zero");
    assertThat(errorInfo.getSqlState()).isEqualTo("22012");
    assertThat(errorInfo.getErrorCode()).isEqualTo(22012);
  }

  /**
   * Ohne {@link SQLException} gibt es die beiden Nebenangaben nicht. Sie bleiben leer — die Ansicht
   * lässt sie dann weg, und die Fehleranzeige scheitert nicht ihrerseits an ihrem Fehlen.
   */
  @Test
  void without_a_sql_exception_the_details_stay_empty() {
    var errorInfo = ReportSqlErrors.describe(new InvalidDataAccessApiUsageException("No value for parameter"));

    assertThat(errorInfo.getErrorClass()).isEqualTo("InvalidDataAccessApiUsageException");
    assertThat(errorInfo.getErrorMessage()).isEqualTo("No value for parameter");
    assertThat(errorInfo.getSqlState()).isNull();
    assertThat(errorInfo.getErrorCode()).isNull();
  }

  @Test
  void a_sql_exception_deeper_in_the_chain_is_found() {
    var sqlException = new SQLException("Timeout", "HYT00", 57014);
    var wrapped = new IllegalStateException("driver gave up", sqlException);

    var errorInfo = ReportSqlErrors.describe(new InvalidDataAccessApiUsageException("call failed", wrapped));

    assertThat(errorInfo.getErrorMessage()).isEqualTo("Timeout");
    assertThat(errorInfo.getSqlState()).isEqualTo("HYT00");
    assertThat(errorInfo.getErrorCode()).isEqualTo(57014);
  }

}
