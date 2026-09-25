package org.tb.common.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Instant;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.tb.auth.domain.SalatUser;
import org.tb.auth.persistence.SalatUserRepository;
import org.tb.common.GlobalConstants;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeRepository;

/**
 * Die andere Hälfte von #1117: Liegen beide Dateien vor, zeigt die Fußleiste Version, Bauzeitpunkt
 * und Commit-Kürzel wie zuvor. Ein Fix, der die Angaben einfach weglässt, käme sonst genauso grün
 * durch wie einer, der sie durchreicht.
 *
 * <p>Die beiden Bohnen werden hier gesetzt statt aus dem Klassenpfad gelesen: ob
 * {@code git.properties} und {@code META-INF/build-info.properties} dort liegen, entscheidet der
 * Maven-Lauf, und genau diese Abhängigkeit soll der Test nicht haben. {@code
 * ProjectInfoAutoConfiguration} tritt dafür von sich aus zurück ({@code ConditionalOnMissingBean}).
 *
 * <p>Eigene H2-Datenbank: siehe {@link FooterWithoutBuildInfoIntegrationTest}.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:salat-1117-complete;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=MySQL;NON_KEYWORDS=YEAR",
    "management.health.mail.enabled=false"
})
@ActiveProfiles({"unittest", "local"})
@Import(FooterWithBuildInfoIntegrationTest.PresentBuildInfo.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class FooterWithBuildInfoIntegrationTest {

  private static final String ADMIN = "a18";
  private static final String VERSION = "5.0.10-TEST";
  private static final String COMMIT_ID = "abc1234";
  private static final Instant BUILD_TIME = Instant.parse("2025-09-25T08:15:30Z");

  @LocalServerPort
  private int port;
  @Autowired
  private EmployeeRepository employeeRepository;
  @Autowired
  private SalatUserRepository salatUserRepository;

  @TestConfiguration
  static class PresentBuildInfo {

    @Bean
    BuildProperties buildProperties() {
      var properties = new Properties();
      properties.setProperty("version", VERSION);
      properties.setProperty("time", String.valueOf(BUILD_TIME.toEpochMilli()));
      return new BuildProperties(properties);
    }

    @Bean
    GitProperties gitProperties() {
      var properties = new Properties();
      properties.setProperty("commit.id.abbrev", COMMIT_ID);
      return new GitProperties(properties);
    }

  }

  @BeforeEach
  void seedAdministrator() {
    if (employeeRepository.findBySign(ADMIN).isPresent()) {
      return;
    }
    SalatUser salatUser = new SalatUser();
    salatUser.setLoginname(ADMIN);
    salatUser.setStatus(GlobalConstants.EMPLOYEE_STATUS_ADM);
    salatUser = salatUserRepository.save(salatUser);

    Employee employee = new Employee();
    employee.setSign(ADMIN);
    employee.setFirstname("Mit");
    employee.setLastname("Bauangaben");
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setSalatUser(salatUser);
    employeeRepository.save(employee);
  }

  @Test
  void the_footer_shows_version_build_time_and_commit_id() throws Exception {
    var response = get("/employees?login-name=" + ADMIN);

    assertThat(response.statusCode()).isEqualTo(200);
    var footer = response.body().substring(response.body().indexOf("<footer"));
    assertThat(footer).contains(VERSION);
    assertThat(footer).contains(BUILD_TIME.toString());
    assertThat(footer).contains(COMMIT_ID);
  }

  private HttpResponse<String> get(String path) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
    return HttpClient.newHttpClient().send(request, BodyHandlers.ofString());
  }

}
