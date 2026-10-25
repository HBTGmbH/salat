package de.hbt.salat.common.graphql;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;
import de.hbt.salat.common.util.ClockProvider;
import de.hbt.salat.common.web.LoginSignProvider;

/**
 * At most so many GraphQL requests per login and minute (concept 6.2): scripts and agents ask in loops, and a limit
 * per document does not stop a thousand cheap documents. A fixed window in memory — the application runs as one
 * instance, and a restart that forgets the counts loses nothing. Over the limit the answer is {@code 429}.
 */
@Slf4j
class GraphqlRateLimitFilter extends OncePerRequestFilter {

  private final LoginSignProvider loginSignProvider;
  private final int requestsPerMinute;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  GraphqlRateLimitFilter(LoginSignProvider loginSignProvider, int requestsPerMinute) {
    this.loginSignProvider = loginSignProvider;
    this.requestsPerMinute = requestsPerMinute;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String login = loginSignProvider.getEffectiveLoginSign();
    if (login != null && !admit(login, ClockProvider.now().truncatedTo(ChronoUnit.MINUTES))) {
      log.info("GraphQL request of {} refused: more than {} per minute", login, requestsPerMinute);
      response.setHeader("Retry-After", "60");
      response.sendError(429, "More than " + requestsPerMinute + " GraphQL requests per minute");
      return;
    }
    chain.doFilter(request, response);
  }

  boolean admit(String login, LocalDateTime minute) {
    var window = windows.compute(login, (key, current) ->
        current == null || !current.minute().equals(minute) ? new Window(minute, 1) : current.next());
    return window.count() <= requestsPerMinute;
  }

  private record Window(LocalDateTime minute, int count) {

    Window next() {
      return new Window(minute, count + 1);
    }
  }
}
