package org.tb.common.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.test.context.ActiveProfiles;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.AuthorizationRule;
import org.tb.auth.domain.SalatUser;
import org.tb.auth.persistence.AuthorizationRuleRepository;
import org.tb.auth.persistence.SalatUserRepository;
import org.tb.common.GlobalConstants;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeRepository;

/**
 * #1145: Ob die Zeile den Nutzer trägt, entscheidet die Reihenfolge der Filter. {@link RequestLogFilter}
 * läuft vor der Sicherheitskette, {@link LoggingFilter} in ihr, und den Sichtwechsel lädt erst
 * {@code UiStateFilter}. Das sieht nur ein Test mit laufendem Container.
 *
 * <p>Eigene H2-Datenbank: die übrigen {@code @SpringBootTest}-Klassen teilen sich eine, und ein
 * zweiter Anwendungskontext mit {@code ddl-auto: create} legte sie beim Hochfahren neu an. Der Cache
 * der Berechtigungsregeln verfällt sofort, sonst sähe der Sichtwechsel die hier angelegte Regel nicht.
 *
 * <p>Was eine Logzeile mitten in der Anfrage trägt, hält ein Filter hinter allen anderen fest.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:salat-1145;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=MySQL;NON_KEYWORDS=YEAR",
    "management.health.mail.enabled=false",
    "salat.auth-service.cache-expiry=0s"
})
@ActiveProfiles({"unittest", "local"})
class RequestLogIntegrationTest {

  /** Ein Administrator braucht keinen Vertrag, sonst antwortete schon {@code EmployeeAccessFilter}. */
  private static final String ADMIN = "rla";
  private static final String OTHER_ADMIN = "rlb";
  private static final String WITHOUT_CONTRACT = "rlc";

  @LocalServerPort
  private int port;
  @Autowired
  private EmployeeRepository employeeRepository;
  @Autowired
  private SalatUserRepository salatUserRepository;
  @Autowired
  private AuthorizationRuleRepository authorizationRuleRepository;

  private final ListAppender<ILoggingEvent> appender = new MdcCapturingAppender();
  private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLogFilter.class);

  @BeforeEach
  void seedAndCaptureLog() {
    employee(ADMIN, GlobalConstants.EMPLOYEE_STATUS_ADM);
    employee(OTHER_ADMIN, GlobalConstants.EMPLOYEE_STATUS_ADM);
    employee(WITHOUT_CONTRACT, GlobalConstants.EMPLOYEE_STATUS_MA);
    if (authorizationRuleRepository.count() == 0) {
      var rule = new AuthorizationRule();
      rule.setCategory("EMPLOYEE");
      rule.setGranteeId(Set.of(ADMIN));
      rule.setObjectId(Set.of(OTHER_ADMIN));
      rule.setAccessLevels(Set.of(AccessLevel.LOGIN));
      authorizationRuleRepository.save(rule);
    }
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void releaseLog() {
    logger.detachAppender(appender);
  }

  /** Pfad der Anfrage → der MDC, den eine Logzeile im Controller trüge. */
  private static final Map<String, Map<String, String>> MDC_DURING_REQUEST = new ConcurrentHashMap<>();

  @TestConfiguration
  static class RecordMdcDuringRequest {

    @Bean
    FilterRegistrationBean<?> recordMdcFilter() {
      var registration = new FilterRegistrationBean<Filter>((request, response, chain) -> {
        var mdc = MDC.getCopyOfContextMap();
        MDC_DURING_REQUEST.put(((HttpServletRequest) request).getRequestURI(), mdc == null ? Map.of() : mdc);
        chain.doFilter(request, response);
      });
      registration.setOrder(Ordered.LOWEST_PRECEDENCE);
      return registration;
    }

  }

  @Test
  void the_line_carries_the_signed_in_user() throws Exception {
    var response = send(get("/employees?login-name=" + ADMIN));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(lineFor("/employees"))
        .containsEntry("response-status", "200")
        .containsEntry("request-query-string", "login-name=" + ADMIN)
        .containsEntry("login-sign", ADMIN)
        .containsEntry("effective-login-sign", ADMIN);
  }

  /** {@code sendError} setzt den Status, bevor der Container die Fehlerseite rendert. */
  @Test
  void the_line_carries_the_status_of_a_denied_request() throws Exception {
    var response = send(get("/dailyreport/dashboard?login-name=" + WITHOUT_CONTRACT));

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(lineFor("/dailyreport/dashboard"))
        .containsEntry("response-status", "403")
        .containsEntry("login-sign", WITHOUT_CONTRACT);
  }

  @Test
  void the_line_carries_no_user_when_the_login_is_unknown() throws Exception {
    var response = send(get("/employees?login-name=unbekannt"));

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(lineFor("/employees"))
        .containsEntry("response-status", "401")
        .doesNotContainKeys("login-sign", "effective-login-sign");
  }

  /**
   * Der Wechsel selbst zeigt den Stand nach der Anfrage, die folgende Anfrage den Stand aus dem
   * Cookie, den {@code UiStateFilter} erst nach {@link LoggingFilter} lädt.
   */
  @Test
  void the_line_carries_the_employee_switched_to() throws Exception {
    long otherAdminId = employeeRepository.findBySign(OTHER_ADMIN).orElseThrow().getId();
    var switched = send(HttpRequest.newBuilder(uri("/auth/switch-login?login-name=" + ADMIN + "&loginEmployeeId=" + otherAdminId))
        .POST(BodyPublishers.noBody()));

    assertThat(switched.statusCode()).isEqualTo(302);
    assertThat(lineFor("/auth/switch-login"))
        .containsEntry("login-sign", ADMIN)
        .containsEntry("effective-login-sign", OTHER_ADMIN);

    String uiStateCookie = switched.headers().allValues("Set-Cookie").stream()
        .filter(cookie -> cookie.startsWith("salat_uistate="))
        .map(cookie -> cookie.substring(0, cookie.indexOf(';')))
        .findFirst().orElseThrow();
    var next = send(get("/employees?login-name=" + ADMIN).header("Cookie", uiStateCookie));

    assertThat(next.statusCode()).isEqualTo(200);
    assertThat(lineFor("/employees"))
        .containsEntry("login-sign", ADMIN)
        .containsEntry("effective-login-sign", OTHER_ADMIN);
    assertThat(MDC_DURING_REQUEST.get("/employees"))
        .containsEntry("login-sign", ADMIN)
        .containsEntry("effective-login-sign", OTHER_ADMIN);
  }

  /** Die Zeile entsteht erst, wenn die Antwort schon unterwegs ist. */
  private Map<String, String> lineFor(String path) {
    return await().atMost(Duration.ofSeconds(5)).until(
        () -> appender.list.stream()
            .map(ILoggingEvent::getMDCPropertyMap)
            .filter(mdc -> path.equals(mdc.get("request-uri")))
            .reduce((first, second) -> second)
            .orElse(null),
        mdc -> mdc != null);
  }

  private HttpRequest.Builder get(String path) {
    return HttpRequest.newBuilder(uri(path)).GET();
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  private static HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
    return HttpClient.newHttpClient().send(request.build(), BodyHandlers.ofString());
  }

  private void employee(String sign, String status) {
    if (employeeRepository.findBySign(sign).isPresent()) {
      return;
    }
    SalatUser salatUser = new SalatUser();
    salatUser.setLoginname(sign);
    salatUser.setStatus(status);
    salatUser = salatUserRepository.save(salatUser);

    Employee employee = new Employee();
    employee.setSign(sign);
    employee.setFirstname("Request");
    employee.setLastname("Log");
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setSalatUser(salatUser);
    employeeRepository.save(employee);
  }

}
