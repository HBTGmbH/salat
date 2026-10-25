package de.hbt.salat.graphqlspike.config;

import static de.hbt.salat.common.exception.ErrorCode.AA_REQUIRED;
import static de.hbt.salat.common.exception.ErrorCode.XX_CONCURRENT_MODIFICATION;

import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
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
 * {@code Accept-Language}. Anything that is not an {@link ErrorCodeException} or a {@link SpikeException} is left
 * to Spring, which answers {@code INTERNAL_ERROR} without details.
 */
@Component
@Profile(GraphqlSpikeConfiguration.PROFILE)
@RequiredArgsConstructor
class SpikeExceptionResolver extends DataFetcherExceptionResolverAdapter {

  private final ErrorCodeViewHelper errorCodeViewHelper;

  @Override
  protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
    if (ex instanceof SpikeException spike) {
      return GraphqlErrorBuilder.newError(env)
          .message(spike.getMessage())
          .errorType(spike.getClassification())
          .extensions(Map.of("code", spike.getCode()))
          .build();
    }
    if (ex instanceof ErrorCodeException errorCode) {
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
    return null;
  }

  /**
   * {@link ErrorCodeViewHelper} reads the language from {@link LocaleContextHolder}, which the
   * {@code DispatcherServlet} sets for the request thread only; a data fetcher may run elsewhere. The language
   * of the GraphQL request is set for the call — a workaround of the spike, see the findings in the concept.
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

  private static ErrorClassification classify(ErrorCodeException ex, ErrorCode first) {
    if (ex instanceof AuthorizationException) {
      return first == AA_REQUIRED ? ErrorType.UNAUTHORIZED : ErrorType.FORBIDDEN;
    }
    if (first == XX_CONCURRENT_MODIFICATION) {
      return SpikeException.CONFLICT;
    }
    return ErrorType.BAD_REQUEST;
  }
}
