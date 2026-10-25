package de.hbt.salat.common.graphql;

import static de.hbt.salat.common.exception.ErrorCode.AA_REQUIRED;
import static de.hbt.salat.common.exception.ErrorCode.XX_CONCURRENT_MODIFICATION;
import static de.hbt.salat.common.exception.ErrorCode.XX_IDEMPOTENCY_KEY_REUSED;

import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper.ViewMessage;

/**
 * The one place where an exception becomes a GraphQL error (concept 6.3): the business code in
 * {@code extensions.code}, the text from the same {@code errorcode.*} keys the pages use, in the language of
 * {@code Accept-Language}. Anything that is not an {@link ErrorCodeException} is left to Spring, which answers
 * {@code INTERNAL_ERROR} without details.
 */
@Component
@RequiredArgsConstructor
class GraphqlExceptionResolver extends DataFetcherExceptionResolverAdapter {

  /** Not among Spring's {@code ErrorType}s; a version conflict and a reused idempotency key answer with it (6.3, 6.5). */
  static final ErrorClassification CONFLICT = ErrorClassification.errorClassification("CONFLICT");

  private static final Set<ErrorCode> CONFLICTS = Set.of(XX_CONCURRENT_MODIFICATION, XX_IDEMPOTENCY_KEY_REUSED);

  private final ErrorCodeViewHelper errorCodeViewHelper;

  @Override
  protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
    if (!(ex instanceof ErrorCodeException errorCode)) {
      return null;
    }
    List<ServiceFeedbackMessage> messages = errorCode.getMessages();
    List<ViewMessage> resolved = resolveIn(env.getLocale(), errorCode);
    ErrorCode first = messages.getFirst().getErrorCode();
    return GraphqlErrorBuilder.newError(env)
        .message(String.join(" ", resolved.stream().map(ViewMessage::resolved).toList()))
        .errorType(classify(errorCode, first))
        .extensions(Map.of(
            "code", first.getCode(),
            "codes", messages.stream().map(message -> message.getErrorCode().getCode()).toList()))
        .build();
  }

  /**
   * {@link ErrorCodeViewHelper} reads the language from {@link LocaleContextHolder}; the language of the GraphQL
   * request is set for the call, so that the answer does not depend on which thread the resolver ran on.
   */
  private List<ViewMessage> resolveIn(Locale locale, ErrorCodeException ex) {
    Locale before = LocaleContextHolder.getLocale();
    LocaleContextHolder.setLocale(locale);
    try {
      return errorCodeViewHelper.toViewMessages(ex);
    } finally {
      LocaleContextHolder.setLocale(before);
    }
  }

  static ErrorClassification classify(ErrorCodeException ex, ErrorCode first) {
    if (ex instanceof AuthorizationException) {
      return first == AA_REQUIRED ? ErrorType.UNAUTHORIZED : ErrorType.FORBIDDEN;
    }
    if (CONFLICTS.contains(first)) {
      return CONFLICT;
    }
    // not found and not readable look the same (concept 6.3): the services answer both with *_NOT_FOUND
    if (first.name().endsWith("_NOT_FOUND")) {
      return ErrorType.NOT_FOUND;
    }
    return ErrorType.BAD_REQUEST;
  }
}
