package de.hbt.salat.graphqlspike.config;

import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.graphql.server.TimeoutWebGraphQlInterceptor;
import de.hbt.salat.auth.domain.AuthorizedUser;

/**
 * The cross-cutting parts of the GraphQL spike (#1532): limits against expensive queries (concept 6.2), the
 * scalars, and the filter that lets only the configured logins in. See {@code de.hbt.salat.graphqlspike} for
 * how the spike is switched on and how it is removed again.
 */
@Configuration
@Profile(GraphqlSpikeConfiguration.PROFILE)
public class GraphqlSpikeConfiguration {

  public static final String PROFILE = "graphql-spike";

  /** Depth and complexity as proposed in the concept (6.2), for business queries ({@link BusinessQueryLimits}). */
  static final int MAX_DEPTH = 8;
  static final int MAX_COMPLEXITY = 200;
  static final Duration TIMEOUT = Duration.ofSeconds(10);

  public GraphqlSpikeConfiguration(Environment environment) {
    if (!environment.matchesProfiles("local")) {
      throw new IllegalStateException("The GraphQL spike (#1532) runs only together with the profile local");
    }
  }

  @Bean
  MaxQueryDepthInstrumentation spikeMaxQueryDepth() {
    return new BusinessQueryLimits.Depth(MAX_DEPTH);
  }

  @Bean
  MaxQueryComplexityInstrumentation spikeMaxQueryComplexity() {
    return new BusinessQueryLimits.Complexity(MAX_COMPLEXITY);
  }

  @Bean
  TimeoutWebGraphQlInterceptor spikeTimeout() {
    return new TimeoutWebGraphQlInterceptor(TIMEOUT);
  }

  @Bean
  RuntimeWiringConfigurer spikeScalars() {
    return wiring -> wiring.scalar(SpikeScalars.DATE).scalar(SpikeScalars.DURATION);
  }

  /**
   * After Spring Security ({@code -100}), so that the login is known; before the GraphQL handler, so that a
   * login not on the list gets neither data nor introspection nor GraphiQL.
   */
  @Bean
  FilterRegistrationBean<SpikeAccessFilter> spikeAccessFilter(AuthorizedUser authorizedUser,
      @Value("${salat.graphql-spike.allowed-signs:}") Set<String> allowedSigns) {
    var registration = new FilterRegistrationBean<>(new SpikeAccessFilter(authorizedUser, allowedSigns));
    registration.addUrlPatterns("/api/graphql", "/api/graphql/*", "/graphiql", "/graphiql/*");
    registration.setOrder(0);
    return registration;
  }
}
