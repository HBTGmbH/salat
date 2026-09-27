package org.tb.common.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LoggingFilterTest {

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  /**
   * Der Thread bedient danach andere Anfragen. Bleibt der Kontext hängen, trägt deren nächste Zeile
   * das Kürzel dieser Anfrage.
   */
  @Test
  void clears_the_mdc_when_the_chain_throws() {
    var filter = new LoggingFilter(Set.of(() -> Map.of("login-sign", "abc")));
    FilterChain chain = (request, response) -> {
      throw new IllegalStateException("boom");
    };

    assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(), chain))
        .isInstanceOf(IllegalStateException.class);

    assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
  }

  /** Wechselt die Anfrage die Sicht, zählt für die Zeile des Request-Logs der Stand danach. */
  @Test
  void hands_the_data_read_after_the_chain_to_the_request_log() throws Exception {
    var effectiveLoginSign = new AtomicReference<>("abc");
    var filter = new LoggingFilter(Set.of(() -> Map.of("effective-login-sign", effectiveLoginSign.get())));
    var request = new MockHttpServletRequest("GET", "/x");

    filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> effectiveLoginSign.set("xyz"));

    assertThat(request.getAttribute(LoggingFilter.MDC_DATA_ATTRIBUTE)).isEqualTo(Map.of("effective-login-sign", "xyz"));
  }

  @Test
  void hands_the_data_to_the_request_log_also_when_the_chain_throws() {
    var filter = new LoggingFilter(Set.of(() -> Map.of("login-sign", "abc")));
    var request = new MockHttpServletRequest("GET", "/x");
    FilterChain chain = (req, res) -> {
      throw new IllegalStateException("boom");
    };

    assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), chain))
        .isInstanceOf(IllegalStateException.class);

    assertThat(request.getAttribute(LoggingFilter.MDC_DATA_ATTRIBUTE)).isEqualTo(Map.of("login-sign", "abc"));
  }

}
