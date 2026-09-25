package org.tb.common.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.tb.auth.domain.SalatUser;
import org.tb.auth.persistence.SalatUserRepository;
import org.tb.common.GlobalConstants;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeRepository;

/**
 * #1117: Fehlt {@code git.properties} oder {@code META-INF/build-info.properties}, gibt es die
 * zugehörige Bohne nicht. Die Fußleiste las sie früher direkt, und weil sie am Ende der Seite
 * steht, brach der Server mitten im Chunked-Strom ab — der Aufrufer bekam eine abgeschnittene
 * Seite und keine Fehlermeldung. Beim {@code java.net.http.HttpClient} äußerte sich das als
 * {@code IOException: chunked transfer encoding, state: READING_LENGTH}.
 *
 * <p>Der Test läuft deshalb über einen echten Port und mit demselben Client: Nur so ist der
 * Abbruch mitten in der Antwort überhaupt vom Ergebnis zu unterscheiden. Ein
 * {@code MockMvc}-Aufruf bekäme die Ausnahme zu sehen, aber nicht die halbe Seite.
 *
 * <p>Beide Bohnen fallen weg, indem {@code ProjectInfoAutoConfiguration} ausgeschlossen wird — die
 * eine Stelle, die sie anlegt. Die Alternative wäre gewesen, die beiden Dateien für den Testlauf
 * aus dem Klassenpfad zu nehmen; das hinge am Maven-Lauf und wäre aus dem Test heraus nicht zu
 * sehen.
 *
 * <p>Eigene H2-Datenbank: die übrigen {@code @SpringBootTest}-Klassen teilen sich eine, und ein
 * zweiter Anwendungskontext mit {@code ddl-auto: create} legte sie beim Hochfahren neu an.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:salat-1117;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=MySQL;NON_KEYWORDS=YEAR",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.info.ProjectInfoAutoConfiguration",
    "management.health.mail.enabled=false"
})
@ActiveProfiles({"unittest", "local"})
@DisplayNameGeneration(ReplaceUnderscores.class)
class FooterWithoutBuildInfoIntegrationTest {

  private static final String ADMIN = "a17";

  @LocalServerPort
  private int port;
  @Autowired
  private ApplicationContext applicationContext;
  @Autowired
  private EmployeeRepository employeeRepository;
  @Autowired
  private SalatUserRepository salatUserRepository;

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
    employee.setFirstname("Ohne");
    employee.setLastname("Bauangaben");
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setSalatUser(salatUser);
    employeeRepository.save(employee);
  }

  /**
   * Die Voraussetzung des Tests. Wäre eine der beiden Bohnen doch da, liefe alles Folgende am
   * Fehlerfall vorbei und behauptete trotzdem, er sei behoben.
   */
  @Test
  void neither_build_nor_git_properties_are_available_in_this_context() {
    assertThat(applicationContext.getBeanNamesForType(BuildProperties.class)).isEmpty();
    assertThat(applicationContext.getBeanNamesForType(GitProperties.class)).isEmpty();
  }

  @Test
  void a_page_renders_completely_without_build_and_git_properties() throws Exception {
    var response = get("/employees?login-name=" + ADMIN);

    assertThat(response.statusCode()).isEqualTo(200);
    // Die Fußleiste steht am Ende des Dokuments: was danach kommt, kommt nur, wenn sie durchlief.
    assertThat(response.body()).contains("<footer");
    assertThat(response.body().trim()).endsWith("</html>");
  }

  /**
   * Was da ist, zeigt die Fußleiste weiter — hier die Serverzeit, die im selben Listenpunkt hinter
   * den drei Bauangaben steht. Ohne sie wäre „vollständig" auch von einer leeren Fußleiste erfüllt.
   */
  @Test
  void the_footer_keeps_the_values_it_does_have() throws Exception {
    var response = get("/employees?login-name=" + ADMIN);

    var footer = response.body().substring(response.body().indexOf("<footer"));
    assertThat(footer).containsPattern("\\d{2}\\.\\d{2}\\.\\d{4} \\d{2}:\\d{2}:\\d{2}");
  }

  private HttpResponse<String> get(String path) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
    return HttpClient.newHttpClient().send(request, BodyHandlers.ofString());
  }

}
