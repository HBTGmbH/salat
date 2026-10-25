package de.hbt.salat.common.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static de.hbt.salat.common.exception.ErrorCode.AA_NEEDS_MANAGER;
import static de.hbt.salat.common.exception.ErrorCode.AA_REQUIRED;
import static de.hbt.salat.common.exception.ErrorCode.TR_EMPLOYEE_CONTRACT_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.TR_EMPLOYEE_ORDER_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.TR_TIME_REPORT_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.XX_CONCURRENT_MODIFICATION;
import static de.hbt.salat.common.exception.ErrorCode.XX_IDEMPOTENCY_KEY_REUSED;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.execution.ErrorType;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.exception.InvalidDataException;

/**
 * The classification a caller acts on, and the operation name in the log (#1532): both are part of the interface, so
 * they are named here one by one instead of following from a naming convention.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class GraphqlErrorAndLogTest {

  @Test
  void only_a_booking_asked_for_by_id_is_not_found() {
    assertThat(classify(new InvalidDataException(TR_TIME_REPORT_NOT_FOUND))).isEqualTo(ErrorType.NOT_FOUND);
    assertThat(GraphqlExceptionResolver.NOT_FOUND).containsExactly(TR_TIME_REPORT_NOT_FOUND);
  }

  /** No contract on the day, no employee order on the suborder: rules not met, not objects missing. */
  @Test
  void a_rule_not_met_is_a_bad_request_whatever_its_constant_is_called() {
    assertThat(classify(new InvalidDataException(TR_EMPLOYEE_CONTRACT_NOT_FOUND))).isEqualTo(ErrorType.BAD_REQUEST);
    assertThat(classify(new InvalidDataException(TR_EMPLOYEE_ORDER_NOT_FOUND))).isEqualTo(ErrorType.BAD_REQUEST);
  }

  @Test
  void conflicts_and_authorization() {
    assertThat(classify(new BusinessRuleException(XX_CONCURRENT_MODIFICATION))).isEqualTo(GraphqlExceptionResolver.CONFLICT);
    assertThat(classify(new BusinessRuleException(XX_IDEMPOTENCY_KEY_REUSED))).isEqualTo(GraphqlExceptionResolver.CONFLICT);
    assertThat(classify(new AuthorizationException(AA_REQUIRED))).isEqualTo(ErrorType.UNAUTHORIZED);
    assertThat(classify(new AuthorizationException(AA_NEEDS_MANAGER))).isEqualTo(ErrorType.FORBIDDEN);
  }

  @Test
  void the_log_takes_only_a_name_graphql_allows() {
    assertThat(GraphqlRequestLogInterceptor.loggable("MeineBuchungen_2")).isEqualTo("MeineBuchungen_2");
    assertThat(GraphqlRequestLogInterceptor.loggable(null)).isEqualTo("(anonymous)");
    assertThat(GraphqlRequestLogInterceptor.loggable("a b")).isEqualTo("(invalid)");
    assertThat(GraphqlRequestLogInterceptor.loggable("x\nGraphQL fake")).isEqualTo("(invalid)");
    assertThat(GraphqlRequestLogInterceptor.loggable("a".repeat(101))).isEqualTo("(invalid)");
  }

  private static Object classify(ErrorCodeException ex) {
    ErrorCode first = ex.getMessages().getFirst().getErrorCode();
    return GraphqlExceptionResolver.classify(ex, first);
  }
}
