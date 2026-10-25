package de.hbt.salat.common.graphql;

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

  private final LoginSignProvider loginSignProvider;

  @Override
  public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
    String operation = request.getOperationName() != null ? request.getOperationName() : "(anonymous)";
    String login = loginSignProvider.getEffectiveLoginSign();
    return chain.next(request).doOnNext(response ->
        log.info("GraphQL {} by {}: {} error(s)", operation, login, response.getErrors().size()));
  }
}
