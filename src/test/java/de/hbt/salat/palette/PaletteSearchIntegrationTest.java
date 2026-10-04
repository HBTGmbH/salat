package de.hbt.salat.palette;

import static de.hbt.salat.testutils.CustomerTestUtils.uniqueShortname;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static de.hbt.salat.common.GlobalConstants.EMPLOYEE_STATUS_BL;
import static de.hbt.salat.common.GlobalConstants.EMPLOYEE_STATUS_MA;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.persistence.SalatUserRepository;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.common.palette.PaletteHit;
import de.hbt.salat.common.palette.PaletteProvider;
import de.hbt.salat.common.palette.PaletteQuery;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.customer.persistence.CustomerRepository;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.persistence.EmployeeRepository;
import de.hbt.salat.employee.persistence.EmployeecontractRepository;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.persistence.CustomerorderRepository;
import de.hbt.salat.order.persistence.SuborderRepository;

/**
 * The object search of the command palette over a real port (#1157), where the unit tests of the
 * providers cannot look: all providers of one search together, with their services and transactions.
 *
 * <p>An exception that leaves a transactional service marks the search's transaction for rollback,
 * even when it is caught afterwards — by a provider, or by the search for a provider denied with an
 * {@code AuthorizationException}. A commit then answered every search with a 500. The selection of
 * the contract is where it happened: it is remembered ({@code fEmployeeContractId}), a link can make
 * it one the user may not read — the page answers 403, the value stays —, and the booking module
 * asked for it through a method that throws.
 *
 * <p>An H2 of its own, like {@code ControllerAuthorizationIntegrationTest}: a second context with
 * {@code ddl-auto: create} must not start on the database the other tests share.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:salat-1157;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=MySQL;NON_KEYWORDS=YEAR",
    "management.health.mail.enabled=false"
})
@ActiveProfiles({"unittest", "local"})
@DisplayNameGeneration(ReplaceUnderscores.class)
class PaletteSearchIntegrationTest {

  private static final String EMPLOYEE = "pma";
  private static final String MANAGER = "pbl";
  /** The login for whom {@link DenyingProvider} is denied. */
  private static final String DENIED = "pdn";
  private static final String SUBORDER = "WARTUNG";

  @LocalServerPort
  private int port;
  @Autowired
  private SalatUserRepository salatUserRepository;
  @Autowired
  private EmployeeRepository employeeRepository;
  @Autowired
  private EmployeecontractRepository employeecontractRepository;
  @Autowired
  private CustomerRepository customerRepository;
  @Autowired
  private CustomerorderRepository customerorderRepository;
  @Autowired
  private SuborderRepository suborderRepository;

  private long managerContractId;

  @BeforeEach
  void seed() {
    contractOf(EMPLOYEE, EMPLOYEE_STATUS_MA);
    contractOf(DENIED, EMPLOYEE_STATUS_MA);
    managerContractId = contractOf(MANAGER, EMPLOYEE_STATUS_BL).getId();
    if (suborderRepository.findAll().iterator().hasNext()) {
      return;
    }
    var customer = new Customer();
    customer.setName("Musterkunde");
    customer.setShortname(uniqueShortname("MUSTER"));
    customer.setAddress("Musterstraße 1");
    customer = customerRepository.save(customer);
    var order = new Customerorder();
    order.setCustomer(customer);
    order.setSign("MUSTER-01");
    order.setDescription("Wartungsvertrag");
    order.setFromDate(LocalDate.of(2000, 1, 1));
    order.setOrderType(OrderType.STANDARD);
    order.setDebithours(Duration.ZERO);
    order = customerorderRepository.save(order);
    var suborder = new Suborder();
    suborder.setCustomerorder(order);
    suborder.setSign(SUBORDER);
    suborder.setDescription("Wartung");
    suborder.setFromDate(LocalDate.of(2000, 1, 1));
    suborder.setDebithours(Duration.ZERO);
    suborderRepository.save(suborder);
  }

  @Test
  void finds_the_suborder() throws Exception {
    var response = get("/palette/search?q=wartung", EMPLOYEE);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("MUSTER-01/" + SUBORDER);
  }

  /** The remembered contract is the manager's; the employee may not read it. */
  @Test
  void a_remembered_contract_the_user_may_not_read_leaves_the_search_answering() throws Exception {
    var response = get("/palette/search?q=wartung&fEmployeeContractId=" + managerContractId, EMPLOYEE);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("MUSTER-01/" + SUBORDER);
  }

  /** The denied provider contributes nothing and takes nobody else's hits along (ADR-0031). */
  @Test
  void a_provider_denied_by_a_service_leaves_the_search_answering() throws Exception {
    var response = get("/palette/search?q=wartung", DENIED);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("MUSTER-01/" + SUBORDER);
  }

  private HttpResponse<String> get(String path, String login) throws Exception {
    var uri = URI.create("http://localhost:" + port + path + "&login-name=" + login);
    return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri).GET().build(), BodyHandlers.ofString());
  }

  private Employeecontract contractOf(String sign, String status) {
    var existing = employeeRepository.findBySign(sign);
    if (existing.isPresent()) {
      return employeecontractRepository.findAllByEmployeeId(existing.get().getId()).getFirst();
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
    employee = employeeRepository.save(employee);
    var contract = new Employeecontract();
    contract.setEmployee(employee);
    contract.setValidFrom(LocalDate.of(2000, 1, 1));
    contract.setDailyWorkingTime(Duration.ofHours(8));
    return employeecontractRepository.save(contract);
  }

  /**
   * For {@link #DENIED}, reads the manager's contract through the method that throws, the way a
   * provider would ask for something the user may not see.
   */
  @TestConfiguration
  static class DenyingProvider {

    @Bean
    PaletteProvider denyingPaletteProvider(AuthorizedUser authorizedUser,
        EmployeecontractService employeecontractService, EmployeeRepository employeeRepository,
        EmployeecontractRepository employeecontractRepository) {
      return new PaletteProvider() {
        @Override
        public List<PaletteHit> search(PaletteQuery query) {
          if (DENIED.equals(authorizedUser.getLoginSign())) {
            var manager = employeeRepository.findBySign(MANAGER).orElseThrow();
            employeecontractService.getEmployeecontractForView(
                employeecontractRepository.findAllByEmployeeId(manager.getId()).getFirst().getId());
          }
          return List.of();
        }
      };
    }
  }
}
