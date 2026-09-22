package org.tb.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_BL;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_BO;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_MA;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_PV;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_RESTRICTED;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.tb.auth.domain.SalatUser;
import org.tb.auth.persistence.SalatUserRepository;
import org.tb.common.GlobalConstants;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeeRepository;
import org.tb.employee.persistence.EmployeecontractRepository;

/**
 * #926: Die Controller tragen ihre Rechteprüfung als {@code @Authorized(requires…)} statt als
 * {@code @PreAuthorize("hasRole(…)")}. Die Annotation ist die Absicht; ob sie greift, entscheidet
 * sich erst im Zusammenspiel von Sicherheitskette, {@code AuthorizationAspect} und
 * Fehlerbehandlung — und genau dort lag der Unterschied: die Ausnahme des Aspekts kam als
 * {@code 500} heraus, bis {@code AuthorizationExceptionHandler} sie beantwortete. Deshalb ein Test
 * über einen echten Port und nicht über die Annotationen.
 *
 * <p>Geprüft wird der direkte Aufruf der URL, nicht das Menü. Ein ausgeblendeter Menüeintrag ist
 * keine Autorisierung — das war die Lücke aus #919.
 *
 * <p>Jede Anmeldung trägt einen heute gültigen Vertrag. Ohne ihn antwortete schon
 * {@code EmployeeAccessFilter} mit 403 (#1054) und jede Zeile wäre aus dem falschen Grund grün.
 *
 * <p>Eigene H2-Datenbank: die übrigen {@code @SpringBootTest}-Klassen teilen sich eine, und ein
 * zweiter Anwendungskontext mit {@code ddl-auto: create} legte sie beim Hochfahren neu an.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:salat-926;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=MySQL;NON_KEYWORDS=YEAR",
    "management.health.mail.enabled=false"
})
@ActiveProfiles({"unittest", "local"})
@DisplayNameGeneration(ReplaceUnderscores.class)
class ControllerAuthorizationIntegrationTest {

  private static final String RESTRICTED = "res";
  private static final String REGULAR = "reg";
  private static final String BACKOFFICE = "bof";
  private static final String PEOPLE_LEAD = "ple";
  private static final String MANAGER = "mgr";

  private static final List<String> ALL_LOGINS =
      List.of(RESTRICTED, REGULAR, BACKOFFICE, PEOPLE_LEAD, MANAGER);

  /** Stammdatensichten: alles ausser {@code RESTRICTED}. */
  private static final List<String> UNRESTRICTED_VIEWS = List.of(
      "/customers",
      "/employees",
      "/employees/contracts",
      "/orders/customerorders",
      "/orders/suborders",
      "/orders/employeeorders");

  /** Pflege der Stammdaten und alles, was Zeiten verschiebt. */
  private static final List<String> MANAGER_VIEWS = List.of(
      "/customers/create",
      "/employees/create",
      "/employees/contracts/create",
      "/orders/customerorders/create",
      "/dailyreports/move");

  /** Rechnungen und die Umsätze aus Buchhaltung und Aufzeichnungen. */
  private static final List<String> BACKOFFICE_VIEWS = List.of(
      "/invoice",
      "/orders/revenue-upload");

  /** Abnahme und die geplanten Berichte. */
  private static final List<String> PEOPLE_LEAD_VIEWS = List.of(
      "/acceptance",
      "/reporting/jobs");

  @LocalServerPort
  private int port;
  @Autowired
  private EmployeeRepository employeeRepository;
  @Autowired
  private EmployeecontractRepository employeecontractRepository;
  @Autowired
  private SalatUserRepository salatUserRepository;

  @BeforeEach
  void seedOneLoginPerRole() {
    employeeWithContract(RESTRICTED, EMPLOYEE_STATUS_RESTRICTED);
    employeeWithContract(REGULAR, EMPLOYEE_STATUS_MA);
    employeeWithContract(BACKOFFICE, EMPLOYEE_STATUS_BO);
    employeeWithContract(PEOPLE_LEAD, EMPLOYEE_STATUS_PV);
    employeeWithContract(MANAGER, EMPLOYEE_STATUS_BL);
  }

  @ParameterizedTest(name = "{1} -> {0}")
  @MethodSource("allowed")
  void an_authorized_login_reaches_the_view(String path, String login) throws Exception {
    assertThat(get(path, login).statusCode()).isEqualTo(200);
  }

  @ParameterizedTest(name = "{1} -> {0}")
  @MethodSource("denied")
  void an_unauthorized_login_is_turned_away_with_403(String path, String login) throws Exception {
    assertThat(get(path, login).statusCode()).isEqualTo(FORBIDDEN.value());
  }

  /**
   * Eine abgewiesene Anfrage endet auf der Fehlerseite der Anwendung, nicht auf der des
   * Servlet-Containers. Genau das war vor {@code AuthorizationExceptionHandler} nicht der Fall: die
   * Ausnahme des Aspekts kam als {@code 500} heraus.
   */
  @ParameterizedTest(name = "{1} -> {0}")
  @MethodSource("oneDenialPerLevel")
  void a_denial_ends_on_the_error_page_of_the_application(String path, String login) throws Exception {
    // copyErrorDetails steht nur in error/error.html und unterscheidet die Fehlerseite der
    // Anwendung von der des Servlet-Containers, unabhängig von der Sprache.
    assertThat(get(path, login).body()).contains("copyErrorDetails");
  }

  private static Stream<Arguments> allowed() {
    return Stream.of(
        pairs(UNRESTRICTED_VIEWS, REGULAR, BACKOFFICE, PEOPLE_LEAD, MANAGER),
        pairs(MANAGER_VIEWS, MANAGER),
        pairs(BACKOFFICE_VIEWS, BACKOFFICE, MANAGER),
        pairs(PEOPLE_LEAD_VIEWS, PEOPLE_LEAD, MANAGER)
    ).flatMap(s -> s);
  }

  private static Stream<Arguments> denied() {
    return Stream.of(
        pairs(UNRESTRICTED_VIEWS, RESTRICTED),
        pairs(MANAGER_VIEWS, RESTRICTED, REGULAR, BACKOFFICE, PEOPLE_LEAD),
        pairs(BACKOFFICE_VIEWS, RESTRICTED, REGULAR, PEOPLE_LEAD),
        pairs(PEOPLE_LEAD_VIEWS, RESTRICTED, REGULAR, BACKOFFICE)
    ).flatMap(s -> s);
  }

  /** Je eine Stichprobe pro Berechtigungsstufe. */
  private static Stream<Arguments> oneDenialPerLevel() {
    return Stream.of(
        Arguments.of("/customers", RESTRICTED),
        Arguments.of("/customers/create", REGULAR),
        Arguments.of("/invoice", REGULAR),
        Arguments.of("/acceptance", REGULAR));
  }

  private static Stream<Arguments> pairs(List<String> paths, String... logins) {
    return paths.stream().flatMap(path -> Stream.of(logins).map(login -> Arguments.of(path, login)));
  }

  private HttpResponse<String> get(String path, String login) throws Exception {
    assertThat(ALL_LOGINS).contains(login);
    var uri = URI.create("http://localhost:" + port + path + "?login-name=" + login);
    return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri).GET().build(), BodyHandlers.ofString());
  }

  private void employeeWithContract(String sign, String status) {
    if (employeeRepository.findBySign(sign).isPresent()) {
      return;
    }
    SalatUser salatUser = new SalatUser();
    salatUser.setLoginname(sign);
    salatUser.setStatus(status);
    salatUser = salatUserRepository.save(salatUser);

    Employee employee = new Employee();
    employee.setSign(sign);
    employee.setFirstname("Vorname");
    employee.setLastname(sign);
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setSalatUser(salatUser);
    employee = employeeRepository.save(employee);

    Employeecontract contract = new Employeecontract();
    contract.setEmployee(employee);
    contract.setValidFrom(LocalDate.of(2000, 1, 1));
    contract.setDailyWorkingTime(Duration.ofHours(8));
    employeecontractRepository.save(contract);
  }

}
