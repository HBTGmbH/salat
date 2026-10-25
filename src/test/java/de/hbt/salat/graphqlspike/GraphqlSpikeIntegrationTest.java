package de.hbt.salat.graphqlspike;

import static de.hbt.salat.testutils.CustomerTestUtils.uniqueShortname;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static de.hbt.salat.common.GlobalConstants.EMPLOYEE_STATUS_BL;
import static de.hbt.salat.common.GlobalConstants.EMPLOYEE_STATUS_MA;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.UUID;
import graphql.introspection.IntrospectionQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.persistence.SalatUserRepository;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.customer.persistence.CustomerRepository;
import de.hbt.salat.dailyreport.domain.Workingday;
import de.hbt.salat.dailyreport.persistence.TimereportRepository;
import de.hbt.salat.dailyreport.persistence.WorkingdayRepository;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.persistence.EmployeeRepository;
import de.hbt.salat.employee.persistence.EmployeecontractRepository;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.persistence.CustomerorderRepository;
import de.hbt.salat.order.persistence.EmployeeorderRepository;
import de.hbt.salat.order.persistence.SuborderRepository;

/**
 * The GraphQL spike over a real port (#1532): the access fence, the runtime context of the resolvers (concept
 * 6.4), the aspects, the error mapping, the limits and the idempotency of creating — the questions the spike is
 * there to answer, asked through the same HTTP chain a caller uses.
 *
 * <p>An H2 of its own, like {@code PaletteSearchIntegrationTest}: a second context with {@code ddl-auto: create}
 * must not start on the database the other tests share.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:salat-1532;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=MySQL;NON_KEYWORDS=YEAR",
    "management.health.mail.enabled=false",
    "salat.graphql-spike.allowed-signs=gqma, gqbl"
})
@ActiveProfiles({"unittest", "local", "graphql-spike"})
@DisplayNameGeneration(ReplaceUnderscores.class)
class GraphqlSpikeIntegrationTest {

  private static final String EMPLOYEE = "gqma";
  private static final String MANAGER = "gqbl";
  private static final String NOT_ENABLED = "gqno";
  /** A Wednesday in the past, inside the year a booking may lie in. */
  private static final LocalDate DAY = LocalDate.now().minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY));

  private static final ObjectMapper JSON = JsonMapper.builder().build();
  private static final String PROBE = """
      { probe {
          direct { ...r } callable { ...r } foreignThread { ...r } batched { index result { ...r } }
      } }
      fragment r on ProbeResult { thread authorizedUserSign authorizedEmployeeSign entityManagerBound error }
      """;

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
  @Autowired
  private EmployeeorderRepository employeeorderRepository;
  @Autowired
  private WorkingdayRepository workingdayRepository;
  @Autowired
  private TimereportRepository timereportRepository;

  private Employeecontract employeeContract;
  private long employeeorderId;

  @BeforeEach
  void seed() {
    employeeContract = contractOf(EMPLOYEE, EMPLOYEE_STATUS_MA);
    contractOf(MANAGER, EMPLOYEE_STATUS_BL);
    contractOf(NOT_ENABLED, EMPLOYEE_STATUS_MA);
    var existing = employeeorderRepository.findAll().iterator();
    if (existing.hasNext()) {
      employeeorderId = existing.next().getId();
      return;
    }
    var customer = new Customer();
    customer.setName("Musterkunde");
    customer.setShortname(uniqueShortname("GQL"));
    customer.setAddress("Musterstraße 1");
    customer = customerRepository.save(customer);
    var order = new Customerorder();
    order.setCustomer(customer);
    order.setSign("ABC-12");
    order.setShortdescription("Wartung");
    order.setDescription("Wartung und Betrieb");
    order.setFromDate(LocalDate.of(2000, 1, 1));
    order.setOrderType(OrderType.STANDARD);
    order.setDebithours(Duration.ZERO);
    order = customerorderRepository.save(order);
    var suborder = new Suborder();
    suborder.setCustomerorder(order);
    suborder.setSign("01");
    suborder.setShortdescription("Betrieb");
    suborder.setDescription("Betrieb");
    suborder.setFromDate(LocalDate.of(2000, 1, 1));
    suborder.setDebithours(Duration.ZERO);
    suborder = suborderRepository.save(suborder);
    var employeeorder = new Employeeorder();
    employeeorder.setSuborder(suborder);
    employeeorder.setEmployeecontract(employeeContract);
    employeeorder.setFromDate(LocalDate.of(2000, 1, 1));
    employeeorderId = employeeorderRepository.save(employeeorder).getId();
    // the service books only on a day with a start of work (TR-0024)
    var workingday = new Workingday();
    workingday.setEmployeecontract(employeeContract);
    workingday.setRefday(DAY);
    workingday.setStartTime(LocalTime.of(8, 0));
    workingdayRepository.save(workingday);
  }

  // --- access ---

  @Test
  void an_enabled_login_reaches_the_endpoint() throws Exception {
    var response = post(EMPLOYEE, "{ me { sign } }");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(json(response).at("/data/me/sign").asString()).isEqualTo(EMPLOYEE);
  }

  @Test
  void a_login_not_enabled_is_refused_before_graphql() throws Exception {
    assertThat(post(NOT_ENABLED, "{ me { sign } }").statusCode()).isEqualTo(403);
    assertThat(get(NOT_ENABLED, "/graphiql").statusCode()).isEqualTo(403);
  }

  /**
   * Refused by the local chain before the spike: it answers 403, and the error page that follows runs through the
   * page chain, which sends a request without login to the development login. Locally a redirect; the JWT chain of
   * an environment answers 401.
   */
  @Test
  void without_login_the_local_chain_refuses() throws Exception {
    var response = post(null, "{ me { sign } }");

    assertThat(response.statusCode()).isEqualTo(302);
  }

  // --- reading ---

  @Test
  void me_answers_from_the_request_scoped_beans_and_the_contract_service() throws Exception {
    var me = json(post(EMPLOYEE, "{ me { employeeId sign name status roles currentContractId } }")).at("/data/me");

    assertThat(me.at("/sign").asString()).isEqualTo(EMPLOYEE);
    assertThat(me.at("/status").asString()).isEqualTo(EMPLOYEE_STATUS_MA);
    assertThat(me.at("/roles").size()).isZero();
    assertThat(me.at("/currentContractId").asString()).isEqualTo(String.valueOf(employeeContract.getId()));
  }

  @Test
  void introspection_works_for_an_enabled_login() throws Exception {
    var body = json(post(EMPLOYEE, "{ __schema { queryType { name } } }"));

    assertThat(body.at("/data/__schema/queryType/name").asString()).isEqualTo("Query");
  }

  // --- runtime context (concept 6.4) ---

  /**
   * Spring for GraphQL runs the data fetchers of a request on the request thread, batch loaders included: the
   * request-scoped beans answer and Open-in-View has its entity manager bound.
   */
  @Test
  void a_resolver_and_a_batch_loader_run_in_the_request_context() throws Exception {
    var probe = json(post(EMPLOYEE, PROBE)).at("/data/probe");

    assertInRequestContext(probe.at("/direct"));
    assertThat(probe.at("/batched").size()).isEqualTo(3);
    probe.at("/batched").forEach(item -> assertInRequestContext(item.at("/result")));
    assertThat(probe.at("/batched/0/result/thread").asString()).isEqualTo(probe.at("/direct/thread").asString());
  }

  /**
   * A resolver that leaves the thread itself loses the request: no login, no entity manager. Nothing fails loudly
   * on the way — the bean throws only when asked.
   */
  @Test
  void on_a_foreign_thread_the_request_scope_and_open_in_view_are_gone() throws Exception {
    var foreign = json(post(EMPLOYEE, PROBE)).at("/data/probe/foreignThread");

    assertThat(foreign.at("/thread").asString()).doesNotStartWith("http-nio");
    assertThat(foreign.at("/authorizedUserSign").isNull()).isTrue();
    assertThat(foreign.at("/authorizedEmployeeSign").isNull()).isTrue();
    assertThat(foreign.at("/entityManagerBound").asBoolean()).isFalse();
    assertThat(foreign.at("/error").asString()).contains("ScopeNotActiveException");
  }

  /**
   * A {@code Callable} needs an executor, and Spring for GraphQL takes Boot's {@code applicationTaskExecutor}. Salat
   * has none: its async executor comes from {@code SalatApplication} as {@code AsyncConfigurer}, not as a bean, and
   * the ETL executor bean makes Boot back off from creating its own.
   */
  @Test
  void a_callable_finds_no_executor() throws Exception {
    var body = json(post(EMPLOYEE, PROBE));

    assertThat(body.at("/data/probe/callable").isNull()).isTrue();
    assertThat(body.at("/errors/0/path/1").asString()).isEqualTo("callable");
    assertThat(body.at("/errors/0/extensions/classification").asString()).isEqualTo("INTERNAL_ERROR");
  }

  private void assertInRequestContext(JsonNode result) {
    assertThat(result.at("/thread").asString()).startsWith("http-nio");
    assertThat(result.at("/authorizedUserSign").asString()).isEqualTo(EMPLOYEE);
    assertThat(result.at("/authorizedEmployeeSign").asString()).isEqualTo(EMPLOYEE);
    assertThat(result.at("/entityManagerBound").asBoolean()).isTrue();
    assertThat(result.at("/error").isNull()).isTrue();
  }

  @Test
  void the_authorization_aspect_guards_a_resolver() throws Exception {
    var refused = json(post(EMPLOYEE, "{ probe { resolverRequiresManager } }"));
    var allowed = json(post(MANAGER, "{ probe { resolverRequiresManager } }"));

    assertThat(refused.at("/errors/0/extensions/classification").asString()).isEqualTo("FORBIDDEN");
    assertThat(refused.at("/errors/0/extensions/code").asString()).isEqualTo("AA-0004");
    assertThat(refused.at("/errors/0/message").asString()).isEqualTo("Erfordert Geschäftsführung-Berechtigung.");
    assertThat(allowed.at("/data/probe/resolverRequiresManager").asBoolean()).isTrue();
  }

  @Test
  void the_authorization_aspect_guards_a_service_called_by_a_resolver() throws Exception {
    var refused = json(post(EMPLOYEE, "{ probe { serviceRequiresManager } }"));
    var allowed = json(post(MANAGER, "{ probe { serviceRequiresManager } }"));

    assertThat(refused.at("/errors/0/extensions/code").asString()).isEqualTo("AA-0004");
    assertThat(allowed.at("/data/probe/serviceRequiresManager").asBoolean()).isTrue();
  }

  // --- limits (concept 6.2) ---

  /**
   * The introspection query GraphiQL and most tools send has a depth of 15; the depth limit of 8 must not lock them
   * out (concept 6.1). Business queries keep the limit ({@code BusinessQueryLimitsTest}).
   */
  @Test
  void the_full_introspection_query_of_the_tools_runs() throws Exception {
    var body = json(post(EMPLOYEE, IntrospectionQuery.INTROSPECTION_QUERY));

    assertThat(body.at("/errors").isMissingNode()).isTrue();
    assertThat(body.at("/data/__schema/queryType/name").asString()).isEqualTo("Query");
  }

  /** What bounds introspection instead: graphql-java's good-faith check refuses a field asked for twice. */
  @Test
  void an_introspection_query_in_bad_faith_is_refused() throws Exception {
    var body = json(post(EMPLOYEE, "{ __schema { types { fields { type { fields { type { name } } } } } } }"));

    assertThat(body.at("/errors/0/message").asString()).isNotBlank();
    assertThat(body.at("/data/__schema").isMissingNode() || body.at("/data/__schema").isNull()).isTrue();
  }

  // --- writing ---

  @Test
  void create_read_update_and_replay_a_booking() throws Exception {
    String key = UUID.randomUUID().toString();
    String create = """
        mutation($key: ID!, $eo: ID!, $day: Date!) {
          createTimereport(idempotencyKey: $key, input: {
            employeeorderId: $eo, date: $day, duration: "PT1H30M", taskDescription: "Spike" }) {
            replayed timereport { id date duration taskDescription status suborder { completeOrderSign } }
          }
        }""";
    Map<String, Object> variables = Map.of("key", key, "eo", employeeorderId, "day", DAY.toString());

    var created = json(post(EMPLOYEE, create, variables)).at("/data/createTimereport");
    assertThat(created.at("/replayed").asBoolean()).isFalse();
    assertThat(created.at("/timereport/duration").asString()).isEqualTo("PT1H30M");
    assertThat(created.at("/timereport/suborder/completeOrderSign").asString()).isEqualTo("ABC-12/01");
    String id = created.at("/timereport/id").asString();

    var replay = json(post(EMPLOYEE, create, variables)).at("/data/createTimereport");
    assertThat(replay.at("/replayed").asBoolean()).isTrue();
    assertThat(replay.at("/timereport/id").asString()).isEqualTo(id);

    var otherInput = json(post(EMPLOYEE, create.replace("PT1H30M", "PT2H"), variables));
    assertThat(otherInput.at("/errors/0/extensions/code").asString()).isEqualTo("SPIKE-0001");
    assertThat(otherInput.at("/errors/0/extensions/classification").asString()).isEqualTo("CONFLICT");

    var list = json(post(EMPLOYEE, "query($day: Date!) { timereports(from: $day, until: $day) { id suborder { id } } }",
        Map.of("day", DAY.toString()))).at("/data/timereports");
    assertThat(list.valueStream().map(timereport -> timereport.at("/id").asString())).contains(id);

    var updated = json(post(EMPLOYEE, """
        mutation($id: ID!) { updateTimereport(id: $id, input: { duration: "PT2H" }) { timereport { duration taskDescription } } }""",
        Map.of("id", id))).at("/data/updateTimereport/timereport");
    assertThat(updated.at("/duration").asString()).isEqualTo("PT2H");
    assertThat(updated.at("/taskDescription").asString()).isEqualTo("Spike");
  }

  /** The status rule of the period (#1164) answers through the service, with its business code. */
  @Test
  void the_status_rule_of_the_period_refuses_a_change() throws Exception {
    var created = json(post(EMPLOYEE, """
        mutation($key: ID!, $eo: ID!, $day: Date!) {
          createTimereport(idempotencyKey: $key, input: {
            employeeorderId: $eo, date: $day, duration: "PT1H", taskDescription: "Vor der Freigabe" }) { timereport { id } }
        }""", Map.of("key", UUID.randomUUID().toString(), "eo", employeeorderId, "day", DAY.toString())));
    String id = created.at("/data/createTimereport/timereport/id").asString();
    // what a release does to the booking
    var timereport = timereportRepository.findById(Long.parseLong(id)).orElseThrow();
    timereport.setStatus(GlobalConstants.TIMEREPORT_STATUS_COMMITTED);
    timereportRepository.save(timereport);

    var refused = json(post(EMPLOYEE, """
        mutation($id: ID!) { updateTimereport(id: $id, input: { duration: "PT3H" }) { timereport { id } } }""",
        Map.of("id", id)));

    assertThat(refused.at("/errors/0/extensions/code").asString()).startsWith("TR-");
    assertThat(refused.at("/errors/0/message").asString()).isNotBlank();
    assertThat(refused.at("/data/updateTimereport").isMissingNode() || refused.at("/data").isNull()).isTrue();
  }

  // --- helpers ---

  private HttpResponse<String> post(String login, String query) throws Exception {
    return post(login, query, Map.of());
  }

  private HttpResponse<String> post(String login, String query, Map<String, Object> variables) throws Exception {
    String body = JSON.writeValueAsString(Map.of("query", query, "variables", variables));
    var request = HttpRequest.newBuilder(uri("/api/graphql", login))
        .header("Content-Type", "application/json")
        .header("Accept-Language", "de")
        .POST(BodyPublishers.ofString(body))
        .build();
    return HttpClient.newHttpClient().send(request, BodyHandlers.ofString());
  }

  private HttpResponse<String> get(String login, String path) throws Exception {
    return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri(path, login)).GET().build(), BodyHandlers.ofString());
  }

  private URI uri(String path, String login) {
    return URI.create("http://localhost:" + port + path + (login == null ? "" : "?login-name=" + login));
  }

  private static JsonNode json(HttpResponse<String> response) {
    return JSON.readTree(response.body());
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
}
