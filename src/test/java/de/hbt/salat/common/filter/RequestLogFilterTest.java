package de.hbt.salat.common.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLogFilterTest {

  private final RequestLogFilter filter = new RequestLogFilter();
  private final ListAppender<ILoggingEvent> appender = new MdcCapturingAppender();
  private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLogFilter.class);

  @BeforeEach
  void captureLog() {
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void releaseLog() {
    logger.detachAppender(appender);
    MDC.clear();
  }

  @Test
  void writes_one_line_with_request_status_duration_and_user() throws Exception {
    var request = request("/dailyreport/list");
    request.setQueryString("fEmployeeContractId=7");
    var response = new MockHttpServletResponse();
    FilterChain chain = (req, res) -> {
      req.setAttribute(LoggingFilter.MDC_DATA_ATTRIBUTE, Map.of("login-sign", "abc", "effective-login-sign", "xyz"));
      response.setStatus(201);
    };

    filter.doFilter(request, response, chain);

    assertThat(appender.list).singleElement().satisfies(event -> {
      assertThat(event.getLevel().toString()).isEqualTo("INFO");
      assertThat(event.getMDCPropertyMap())
          .containsEntry("request-method", "GET")
          .containsEntry("request-uri", "/dailyreport/list")
          .containsEntry("request-query-string", "fEmployeeContractId=7")
          .containsEntry("response-status", "201")
          .containsEntry("login-sign", "abc")
          .containsEntry("effective-login-sign", "xyz")
          .containsKey("duration-ms");
      assertThat(event.getMDCPropertyMap().get("duration-ms")).containsOnlyDigits();
    });
    assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
  }

  /** Abgewiesen vor der Anmeldung oder frei zugänglich: die Zeile entsteht, nur ohne Nutzer. */
  @Test
  void writes_the_line_without_user_when_nobody_is_signed_in() throws Exception {
    var response = new MockHttpServletResponse();

    filter.doFilter(request("/api/employee-orders/list"), response, (req, res) -> response.setStatus(401));

    assertThat(appender.list).singleElement().satisfies(event -> assertThat(event.getMDCPropertyMap())
        .containsEntry("response-status", "401")
        .containsEntry("request-query-string", "<empty>")
        .doesNotContainKeys("login-sign", "effective-login-sign"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/js/salat.js", "/style/print.css", "/images/logo-salat-neu.png", "/favicon.ico", "/webjars/bootstrap/css/bootstrap.min.css", "/actuator/health"})
  void writes_no_line_for_static_resources_and_the_health_check(String path) throws Exception {
    var chainCalled = new boolean[1];

    filter.doFilter(request(path), new MockHttpServletResponse(), (req, res) -> chainCalled[0] = true);

    assertThat(chainCalled[0]).isTrue();
    assertThat(appender.list).isEmpty();
  }

  /**
   * Die Response steht noch auf 200, wenn die Ausnahme hier vorbeikommt. Beantwortet wird sie danach
   * vom Container, und der antwortet mit 500.
   */
  @Test
  void logs_500_and_rethrows_when_the_chain_throws() {
    var request = request("/employees");
    FilterChain chain = (req, res) -> {
      req.setAttribute(LoggingFilter.MDC_DATA_ATTRIBUTE, Map.of("login-sign", "abc"));
      throw new IllegalStateException("boom");
    };

    assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), chain))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");

    assertThat(appender.list).singleElement().satisfies(event -> assertThat(event.getMDCPropertyMap())
        .containsEntry("response-status", "500")
        .containsEntry("login-sign", "abc"));
    assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
  }

  /** Ist die Antwort schon unterwegs, ändert der Container ihren Status nicht mehr. */
  @Test
  void keeps_the_committed_status_when_the_chain_throws_afterwards() throws Exception {
    var response = new MockHttpServletResponse();
    FilterChain chain = (req, res) -> {
      response.setStatus(200);
      response.flushBuffer();
      throw new IllegalStateException("boom");
    };

    assertThatThrownBy(() -> filter.doFilter(request("/employees"), response, chain))
        .isInstanceOf(IllegalStateException.class);

    assertThat(appender.list).singleElement().satisfies(event -> assertThat(event.getMDCPropertyMap())
        .containsEntry("response-status", "200"));
  }

  private static MockHttpServletRequest request(String path) {
    var request = new MockHttpServletRequest("GET", path);
    request.setServletPath(path);
    return request;
  }

}
