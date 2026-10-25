package de.hbt.salat.graphqlspike.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;
import de.hbt.salat.auth.domain.AuthorizedUser;

/**
 * Lets only the logins in {@code salat.graphql-spike.allowed-signs} reach the spike (#1532); everybody else gets
 * 403 before GraphQL sees the request. Not a rule of the concept — the finished interface is open to every
 * person within their rights (concept 5.2) — but the fence around an experiment.
 */
@Slf4j
class SpikeAccessFilter extends OncePerRequestFilter {

  private final AuthorizedUser authorizedUser;
  private final Set<String> allowedSigns;

  SpikeAccessFilter(AuthorizedUser authorizedUser, Set<String> allowedSigns) {
    this.authorizedUser = authorizedUser;
    this.allowedSigns = allowedSigns.stream()
        .map(String::trim)
        .filter(sign -> !sign.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!authorizedUser.isAuthenticated()) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    if (!allowedSigns.contains(authorizedUser.getLoginSign())) {
      log.info("GraphQL spike refused for {}", authorizedUser.getLoginSign());
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "GraphQL spike (#1532): login not enabled");
      return;
    }
    chain.doFilter(request, response);
  }
}
