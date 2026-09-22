package org.tb.employee.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.tb.common.exception.ErrorCode.EC_NO_CURRENT_CONTRACT;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.tb.auth.domain.SalatUser;
import org.tb.auth.persistence.SalatUserRepository;
import org.tb.common.GlobalConstants;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeRepository;

/**
 * #1054: Die Antwort auf einen fehlenden Vertrag entsteht erst im Zusammenspiel mit dem
 * Servlet-Container - {@code sendError} führt auf {@code /error}, und in dieser Weiterleitung gelingt
 * die Authentifizierung erneut. Genau daran ging die Anwendung vorher zugrunde, und genau das sieht
 * ein Test ohne laufenden Container nicht. Der Test läuft deshalb über einen echten Port.
 *
 * <p>Eigene H2-Datenbank: die übrigen {@code @SpringBootTest}-Klassen teilen sich eine, und ein
 * zweiter Anwendungskontext mit {@code ddl-auto: create} legte sie beim Hochfahren neu an.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:salat-1054;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=MySQL;NON_KEYWORDS=YEAR",
    "management.health.mail.enabled=false"
})
@ActiveProfiles({"unittest", "local"})
class EmployeeAccessIntegrationTest {

  private static final String WITHOUT_CONTRACT = "nov";
  private static final String ADMIN_WITHOUT_CONTRACT = "adv";

  @LocalServerPort
  private int port;
  @Autowired
  private EmployeeRepository employeeRepository;
  @Autowired
  private SalatUserRepository salatUserRepository;

  @BeforeEach
  void seedEmployeesWithoutContract() {
    employeeWithoutContract(WITHOUT_CONTRACT, GlobalConstants.EMPLOYEE_STATUS_MA);
    employeeWithoutContract(ADMIN_WITHOUT_CONTRACT, GlobalConstants.EMPLOYEE_STATUS_ADM);
  }

  @Test
  void a_page_request_ends_in_the_error_page_of_the_application() throws Exception {
    var response = get("/dailyreport/dashboard?login-name=" + WITHOUT_CONTRACT);

    assertThat(response.statusCode()).isEqualTo(FORBIDDEN.value());
    // copyErrorDetails steht nur in error/error.html - das unterscheidet die Fehlerseite der
    // Anwendung von der des Servlet-Containers, und zwar unabhaengig von der Sprache.
    assertThat(response.body()).contains("copyErrorDetails");
    // Der Grund steht auf der Seite: die Meldung beider Sprachbuendel nennt das Kuerzel.
    assertThat(response.body()).contains(WITHOUT_CONTRACT);
  }

  @Test
  void an_api_request_ends_in_a_problem_document() throws Exception {
    var response = get("/api/orders?login-name=" + WITHOUT_CONTRACT);

    assertThat(response.statusCode()).isEqualTo(FORBIDDEN.value());
    assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
        contentType -> assertThat(contentType).startsWith("application/problem+json"));
    assertThat(response.body()).contains(EC_NO_CURRENT_CONTRACT.getCode());
  }

  /**
   * Ein Administrator ist kein Mitarbeiter und hat deshalb nie einen Vertrag (→ AGENTS.md,
   * "Role semantics"). Für ihn darf der fehlende Vertrag kein Hindernis sein.
   */
  @Test
  void an_administrator_gets_through_without_a_contract() throws Exception {
    var response = get("/employees?login-name=" + ADMIN_WITHOUT_CONTRACT);

    assertThat(response.statusCode()).isEqualTo(200);
  }

  private HttpResponse<String> get(String path) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
    return HttpClient.newHttpClient().send(request, BodyHandlers.ofString());
  }

  private void employeeWithoutContract(String sign, String status) {
    if (employeeRepository.findBySign(sign).isPresent()) {
      return;
    }
    SalatUser salatUser = new SalatUser();
    salatUser.setLoginname(sign);
    salatUser.setStatus(status);
    salatUser = salatUserRepository.save(salatUser);

    Employee employee = new Employee();
    employee.setSign(sign);
    employee.setFirstname("Ohne");
    employee.setLastname("Vertrag");
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setSalatUser(salatUser);
    employeeRepository.save(employee);
  }

}
