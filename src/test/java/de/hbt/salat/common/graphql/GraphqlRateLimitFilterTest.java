package de.hbt.salat.common.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import de.hbt.salat.common.util.ClockProvider;
import de.hbt.salat.common.web.LoginSignProvider;

/** At most so many GraphQL requests per login and minute (#1532, concept 6.2). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class GraphqlRateLimitFilterTest {

  private static final LocalDateTime MINUTE = LocalDateTime.of(2026, 10, 26, 9, 15);

  private final LoginSignProvider loginSignProvider = mock(LoginSignProvider.class);
  private final GraphqlRateLimitFilter filter = new GraphqlRateLimitFilter(loginSignProvider, 3);

  @Test
  void the_requests_up_to_the_limit_pass_and_the_next_is_refused() {
    assertThat(filter.admit("ma", MINUTE)).isTrue();
    assertThat(filter.admit("ma", MINUTE)).isTrue();
    assertThat(filter.admit("ma", MINUTE)).isTrue();
    assertThat(filter.admit("ma", MINUTE)).isFalse();
  }

  @Test
  void every_login_counts_on_its_own() {
    for (int i = 0; i < 3; i++) {
      filter.admit("ma", MINUTE);
    }

    assertThat(filter.admit("bl", MINUTE)).isTrue();
  }

  @Test
  void a_new_minute_starts_from_zero() {
    for (int i = 0; i < 4; i++) {
      filter.admit("ma", MINUTE);
    }

    assertThat(filter.admit("ma", MINUTE.plusMinutes(1))).isTrue();
  }

  @AfterEach
  void resetClock() {
    ClockProvider.reset();
  }

  @Test
  void over_the_limit_the_answer_is_429() throws Exception {
    ClockProvider.setClock(Clock.fixed(MINUTE.plusSeconds(30).atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
    when(loginSignProvider.getEffectiveLoginSign()).thenReturn("ma");
    var refused = new MockHttpServletResponse();
    for (int i = 0; i < 3; i++) {
      filter.doFilter(new MockHttpServletRequest("POST", "/api/graphql"), new MockHttpServletResponse(), new MockFilterChain());
    }

    filter.doFilter(new MockHttpServletRequest("POST", "/api/graphql"), refused, new MockFilterChain());

    assertThat(refused.getStatus()).isEqualTo(429);
    assertThat(refused.getHeader("Retry-After")).isEqualTo("60");
  }
}
