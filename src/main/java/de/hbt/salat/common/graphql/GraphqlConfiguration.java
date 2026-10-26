package de.hbt.salat.common.graphql;

import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.graphql.server.TimeoutWebGraphQlInterceptor;
import de.hbt.salat.common.web.LoginSignProvider;

/**
 * The cross-cutting parts of the GraphQL interface (#1532, ADR-0044): limits against expensive queries (concept
 * 6.2), the scalars, the limit of requests per login and the log of every operation. The resolvers live in the
 * modules, in their sub-package {@code graphql}, and the schema in one file per module under
 * {@code classpath:graphql/}.
 */
@Configuration
public class GraphqlConfiguration {

  /** Depth and complexity of business queries (concept 6.2); introspection is bounded otherwise ({@link BusinessQueryLimits}). */
  static final int MAX_DEPTH = 8;
  static final int MAX_COMPLEXITY = 200;
  static final Duration TIMEOUT = Duration.ofSeconds(10);

  @Bean
  MaxQueryDepthInstrumentation graphqlMaxQueryDepth() {
    return new BusinessQueryLimits.Depth(MAX_DEPTH);
  }

  @Bean
  MaxQueryComplexityInstrumentation graphqlMaxQueryComplexity() {
    return new BusinessQueryLimits.Complexity(MAX_COMPLEXITY);
  }

  /** Refuses a fragment-spread ladder before normalization, where depth and complexity do not reach it (concept 6.2). */
  @Bean
  FragmentExpansionInstrumentation graphqlFragmentExpansionLimit() {
    return new FragmentExpansionInstrumentation();
  }

  @Bean
  TimeoutWebGraphQlInterceptor graphqlTimeout() {
    return new TimeoutWebGraphQlInterceptor(TIMEOUT);
  }

  @Bean
  RuntimeWiringConfigurer graphqlScalars() {
    return wiring -> wiring.scalar(GraphqlScalars.DATE).scalar(GraphqlScalars.DURATION).scalar(GraphqlScalars.TIME);
  }

  @Bean
  GraphqlRequestLogInterceptor graphqlRequestLog(LoginSignProvider loginSignProvider) {
    return new GraphqlRequestLogInterceptor(loginSignProvider);
  }

  /** Before the rate-limit filter: an oversized body is refused before it is counted or read (concept 6.2). */
  @Bean
  FilterRegistrationBean<GraphqlRequestSizeLimitFilter> graphqlRequestSizeLimitFilter(
      @Value("${salat.graphql.max-request-bytes:1048576}") long maxBytes,
      @Value("${spring.graphql.http.path:/graphql}") String path) {
    var registration = new FilterRegistrationBean<>(new GraphqlRequestSizeLimitFilter(maxBytes));
    registration.addUrlPatterns(path);
    registration.setOrder(-1);
    return registration;
  }

  /** After Spring Security ({@code -100}), so that the login is known. */
  @Bean
  FilterRegistrationBean<GraphqlRateLimitFilter> graphqlRateLimitFilter(LoginSignProvider loginSignProvider,
      @Value("${salat.graphql.requests-per-minute:120}") int requestsPerMinute,
      @Value("${spring.graphql.http.path:/graphql}") String path) {
    var registration = new FilterRegistrationBean<>(new GraphqlRateLimitFilter(loginSignProvider, requestsPerMinute));
    registration.addUrlPatterns(path);
    registration.setOrder(0);
    return registration;
  }
}
