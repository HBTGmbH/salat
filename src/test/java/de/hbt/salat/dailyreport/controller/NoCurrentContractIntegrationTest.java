package de.hbt.salat.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static de.hbt.salat.common.GlobalConstants.EMPLOYEE_STATUS_ADM;
import static de.hbt.salat.common.GlobalConstants.EMPLOYEE_STATUS_BL;
import static de.hbt.salat.common.GlobalConstants.EMPLOYEE_STATUS_MA;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.persistence.SalatUserRepository;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.persistence.EmployeeRepository;

/**
 * Eine Anmeldung kommt ohne heute gültigen Vertrag herein, gleich mit welchem Status (#1588; bis dahin
 * nur adm). Jede Seite, die ohne gewählten Vertrag den eigenen zeigt, kommt ohne ihn aus: kein Fehler
 * 500 (#1559).
 * Über einen echten Port, weil erst die ganze Kette — Filter, Controller, Template — sagt, ob die Seite steht.
 *
 * <p>Eigene H2-Datenbank, aus demselben Grund wie in {@code ControllerAuthorizationIntegrationTest}.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:salat-1559;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=MySQL;NON_KEYWORDS=YEAR",
    "management.health.mail.enabled=false"
})
@ActiveProfiles({"unittest", "local"})
@DisplayNameGeneration(ReplaceUnderscores.class)
class NoCurrentContractIntegrationTest {

  private static final String ADM = "nca";
  private static final String MA = "ncm";
  private static final String BL = "ncb";

  @LocalServerPort
  private int port;
  @Autowired
  private EmployeeRepository employeeRepository;
  @Autowired
  private SalatUserRepository salatUserRepository;

  @BeforeEach
  void seedLoginsWithoutContract() {
    seedLoginWithoutContract(ADM, EMPLOYEE_STATUS_ADM);
    seedLoginWithoutContract(MA, EMPLOYEE_STATUS_MA);
    seedLoginWithoutContract(BL, EMPLOYEE_STATUS_BL);
  }

  private void seedLoginWithoutContract(String sign, String status) {
    if (employeeRepository.findBySign(sign).isPresent()) {
      return;
    }
    var salatUser = new SalatUser();
    salatUser.setLoginname(sign);
    salatUser.setStatus(status);
    salatUser = salatUserRepository.save(salatUser);

    var employee = new Employee();
    employee.setSign(sign);
    employee.setFirstname("Vorname");
    employee.setLastname(sign);
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setSalatUser(salatUser);
    employeeRepository.save(employee);
  }

  @ParameterizedTest
  @CsvSource({
      "nca, /dailyreport/dashboard", "nca, /my-accounts",
      "ncm, /dailyreport/dashboard", "ncm, /my-accounts",
      "ncb, /dailyreport/dashboard", "ncb, /my-accounts"})
  void the_page_shows_the_hint(String login, String path) throws Exception {
    var response = get(login, path);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("id=\"no-current-contract\"");
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "/dailyreport/daily",
      "/dailyreport/matrix",
      "/dailyreport/csv",
      "/dailyreport/csv/export?month=2026-10",
      "/dailyreport/timereports",
      "/dailyreport/timereports/new",
      "/release",
      "/release/review",
      "/settings",
      "/palette/search?q=ab"})
  void the_page_does_not_fail(String path) throws Exception {
    for (var login : new String[] {ADM, MA, BL}) {
      var response = get(login, path);

      assertThat(response.statusCode()).as(login + " " + path).isLessThan(500);
      // error-details-text steht nur auf der Fehlerseite der Anwendung
      assertThat(response.body()).as(login + " " + path).doesNotContain("error-details-text");
    }
  }

  /** Die Rolle hängt am Login, nicht am Vertrag: ein Manager ohne Vertrag pflegt Verträge wie mit (#1588). */
  @Test
  void a_manager_reaches_the_pages_of_the_role() throws Exception {
    var response = get(BL, "/employees/contracts");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).doesNotContain("error-details-text");
  }

  @Test
  void the_start_page_leads_to_the_dashboard() throws Exception {
    var response = get(MA, "/");

    assertThat(response.statusCode()).isEqualTo(301);
    assertThat(response.headers().firstValue("Location")).hasValueSatisfying(location ->
        assertThat(location).endsWith("/dailyreport/dashboard"));
  }

  private HttpResponse<String> get(String login, String path) throws Exception {
    var uri = URI.create("http://localhost:" + port + path + (path.contains("?") ? "&" : "?") + "login-name=" + login);
    return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri).GET().build(), BodyHandlers.ofString());
  }
}
