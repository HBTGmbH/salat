package de.hbt.salat.graphqlspike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static de.hbt.salat.common.GlobalConstants.EMPLOYEE_STATUS_BL;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.test.context.ActiveProfiles;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.persistence.SalatUserRepository;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.persistence.EmployeeRepository;
import de.hbt.salat.employee.persistence.EmployeecontractRepository;
import de.hbt.salat.graphqlspike.config.SpikeIdempotency;

/**
 * Without the profile {@code graphql-spike} there is no GraphQL at all (#1532): the dependency is on the classpath
 * of every build, so this is what keeps it out of an environment.
 *
 * <p>The configuration of {@code EmployeeAccessIntegrationTest}, so that both share one context.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:salat-1054;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=MySQL;NON_KEYWORDS=YEAR",
    "management.health.mail.enabled=false"
})
@ActiveProfiles({"unittest", "local"})
@DisplayNameGeneration(ReplaceUnderscores.class)
class GraphqlSpikeSwitchedOffIntegrationTest {

  private static final String LOGIN = "gqloff";

  @LocalServerPort
  private int port;
  @Autowired
  private ApplicationContext context;
  @Autowired
  private SalatUserRepository salatUserRepository;
  @Autowired
  private EmployeeRepository employeeRepository;
  @Autowired
  private EmployeecontractRepository employeecontractRepository;

  @Test
  void the_auto_configuration_finds_no_schema_and_starts_nothing() {
    assertThat(context.getBeanNamesForType(GraphQlSource.class)).isEmpty();
    assertThat(context.getBeanNamesForType(SpikeIdempotency.class)).isEmpty();
  }

  @Test
  void the_endpoint_does_not_exist() throws Exception {
    if (salatUserRepository.findByLoginname(LOGIN).isEmpty()) {
      var user = new SalatUser();
      user.setLoginname(LOGIN);
      user.setStatus(EMPLOYEE_STATUS_BL);
      user = salatUserRepository.save(user);
      // a login without a person or a current contract is refused before any handler (EmployeeAccessFilter)
      var employee = new Employee();
      employee.setSign(LOGIN);
      employee.setFirstname("Vorname");
      employee.setLastname(LOGIN);
      employee.setGender(GlobalConstants.GENDER_FEMALE);
      employee.setSalatUser(user);
      employee = employeeRepository.save(employee);
      var contract = new Employeecontract();
      contract.setEmployee(employee);
      contract.setValidFrom(LocalDate.of(2000, 1, 1));
      contract.setDailyWorkingTime(Duration.ofHours(8));
      employeecontractRepository.save(contract);
    }
    var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/graphql?login-name=" + LOGIN))
        .header("Content-Type", "application/json")
        .POST(BodyPublishers.ofString("{\"query\":\"{ __typename }\"}"))
        .build();

    var response = HttpClient.newHttpClient().send(request, BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(404);
  }
}
