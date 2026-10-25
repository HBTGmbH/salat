package de.hbt.salat.common.graphql;

import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import reactor.core.publisher.Mono;
import de.hbt.salat.common.web.LoginSignProvider;

/**
 * Logs every GraphQL request with the login and the name of its operation (concept 6.6), so that the production
 * report can count the use of the interface per operation as it does per REST endpoint. The request log only sees
 * {@code POST /api/graphql}. A mutation additionally logs the id it changed, in its resolver.
 */
@Slf4j
@RequiredArgsConstructor
class GraphqlRequestLogInterceptor implements WebGraphQlInterceptor {

  private static final Pattern NAME = Pattern.compile("[_A-Za-z][_0-9A-Za-z]{0,99}");

  private final LoginSignProvider loginSignProvider;

  @Override
  public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
    String operation = loggable(request.getOperationName());
    String login = loginSignProvider.getEffectiveLoginSign();
    return chain.next(request).doOnNext(response ->
        log.info("GraphQL {} by {}: {} error(s)", operation, login, response.getErrors().size()));
  }

  /**
   * The name as the caller sent it, before graphql-java checks it against the document — so only a name GraphQL
   * allows, of bounded length. Whatever else the caller chooses would land in the log and in the count per operation.
   */
  static String loggable(String operationName) {
    if (operationName == null) {
      return "(anonymous)";
    }
    return NAME.matcher(operationName).matches() ? operationName : "(invalid)";
  }
}
