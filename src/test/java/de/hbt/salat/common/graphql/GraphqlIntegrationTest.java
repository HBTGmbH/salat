package de.hbt.salat.common.graphql;

import static de.hbt.salat.testutils.CustomerTestUtils.uniqueShortname;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static de.hbt.salat.common.GlobalConstants.EMPLOYEE_STATUS_BL;
import static de.hbt.salat.common.GlobalConstants.EMPLOYEE_STATUS_MA;
import static de.hbt.salat.common.GlobalConstants.EMPLOYEE_STATUS_RESTRICTED;

import graphql.introspection.IntrospectionQuery;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import de.hbt.salat.dailyreport.persistence.TimereportRepository;
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
 * The GraphQL interface over a real port (#1532): the same HTTP chain a caller uses, with the services, the
 * aspects, the transactions and the database behind it. What the spike showed about the runtime context — resolvers
 * and batch loaders run on the request thread — is what every test here relies on.
 *
 * <p>An H2 of its own, like {@code PaletteSearchIntegrationTest}: a second context with {@code ddl-auto: create}
 * must not start on the database the other tests share. The tests share it among themselves, so every assertion is
 * about data the test created itself.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:salat-1532;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=MySQL;NON_KEYWORDS=YEAR",
    "management.health.mail.enabled=false"
})
@ActiveProfiles({"unittest", "local"})
@DisplayNameGeneration(ReplaceUnderscores.class)
class GraphqlIntegrationTest {

  private static final String EMPLOYEE = "gqma";
  private static final String MANAGER = "gqbl";
  /** A second employee without any role, to show what she does not see. */
  private static final String OTHER_EMPLOYEE = "gqm2";
  private static final String RESTRICTED = "gqrs";
  /** A person whose employee order on the suborder is split into two periods. */
  private static final String MOVER = "gqmv";
  /** A Wednesday in the past, inside the year a booking may lie in. */
  private static final LocalDate DAY = LocalDate.now().minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY));

  private static final ObjectMapper JSON = JsonMapper.builder().build();

  private static final String CREATE = """
      mutation($key: ID!, $suborder: ID!, $day: Date!, $duration: Duration!, $text: String!) {
        createTimereport(idempotencyKey: $key, input: {
          suborderId: $suborder, date: $day, duration: $duration, taskDescription: $text }) {
          replayed timereport { id version date duration taskDescription status suborder { completeOrderSign } }
        }
      }""";

  private static final String LIST = """
      query($filter: TimereportListFilter!) {
        timereportList(filter: $filter) {
          totalCount totalDuration truncated items { id employee { id sign name } suborder { completeOrderSign } }
        }
      }""";

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
  private TimereportRepository timereportRepository;

  private Employeecontract employeeContract;
  private long suborderId;

  @BeforeEach
  void seed() throws Exception {
    employeeContract = contractOf(EMPLOYEE, EMPLOYEE_STATUS_MA);
    contractOf(MANAGER, EMPLOYEE_STATUS_BL);
    contractOf(OTHER_EMPLOYEE, EMPLOYEE_STATUS_MA);
    contractOf(RESTRICTED, EMPLOYEE_STATUS_RESTRICTED);
    var existing = suborderRepository.findAll().iterator();
    if (existing.hasNext()) {
      suborderId = existing.next().getId();
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
    // both responsible persons are mandatory in the form; the read rule of bookings asks for them
    var manager = employeeRepository.findBySign(MANAGER).orElseThrow();
    order.setResponsibleHbt(List.of(manager));
    order.setRespEmpHbtContract(manager);
    order = customerorderRepository.save(order);
    var suborder = new Suborder();
    suborder.setCustomerorder(order);
    suborder.setSign("01");
    suborder.setShortdescription("Betrieb");
    suborder.setDescription("Betrieb");
    suborder.setFromDate(LocalDate.of(2000, 1, 1));
    suborder.setDebithours(Duration.ZERO);
    suborder = suborderRepository.save(suborder);
    suborderId = suborder.getId();
    var employeeorder = new Employeeorder();
    employeeorder.setSuborder(suborder);
    employeeorder.setEmployeecontract(employeeContract);
    employeeorder.setFromDate(LocalDate.of(2000, 1, 1));
    employeeorderRepository.save(employeeorder);
    // the start of work of the day the tests book on, through the interface itself
    post(EMPLOYEE, """
        mutation($day: Date!) { upsertWorkingday(input: { date: $day, startTime: "08:00" }) { date } }""",
        Map.of("day", DAY.toString()));
  }

  // --- access and reading ---

  @Test
  void every_logged_in_person_reaches_the_interface() throws Exception {
    for (String login : List.of(EMPLOYEE, MANAGER, OTHER_EMPLOYEE, RESTRICTED)) {
      var response = post(login, "{ me { sign } }");

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(json(response).at("/data/me/sign").asString()).isEqualTo(login);
    }
  }

  @Test
  void me_answers_from_the_request_scoped_beans_and_the_contract_service() throws Exception {
    var me = json(post(EMPLOYEE, "{ me { employeeId sign name status roles currentContractId } }")).at("/data/me");

    assertThat(me.at("/sign").asString()).isEqualTo(EMPLOYEE);
    assertThat(me.at("/status").asString()).isEqualTo(EMPLOYEE_STATUS_MA);
    assertThat(me.at("/roles").size()).isZero();
    assertThat(me.at("/currentContractId").asString()).isEqualTo(String.valueOf(employeeContract.getId()));
  }

  /** {@code order} extends the type of {@code employee} (concept 3.7). */
  @Test
  void me_lists_what_the_person_may_book_on() throws Exception {
    var own = json(post(EMPLOYEE, "query($day: Date!) { me { bookableSuborders(on: $day) { suborderId completeOrderSign } } }",
        Map.of("day", DAY.toString()))).at("/data/me/bookableSuborders");
    var other = json(post(OTHER_EMPLOYEE, "query($day: Date!) { me { bookableSuborders(on: $day) { suborderId } } }",
        Map.of("day", DAY.toString()))).at("/data/me/bookableSuborders");

    assertThat(own.size()).isEqualTo(1);
    assertThat(own.at("/0/suborderId").asString()).isEqualTo(String.valueOf(suborderId));
    assertThat(own.at("/0/completeOrderSign").asString()).isEqualTo("ABC-12/01");
    assertThat(other.size()).isZero();
  }

  @Test
  void the_schema_is_published_as_a_file() throws Exception {
    var response = get(EMPLOYEE, "/api/graphql/schema");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("type Timereport");
  }

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

  // --- the booking list (TimereportVisibility, #1092) ---

  @Test
  void the_manager_sees_the_bookings_of_an_employee() throws Exception {
    String id = createBooking("Für die Liste");

    var page = json(post(MANAGER, LIST, Map.of("filter", listFilter(employeeIdOf(EMPLOYEE))))).at("/data/timereportList");

    assertThat(ids(page)).contains(id);
    assertThat(page.at("/items").valueStream().map(item -> item.at("/employee/sign").asString())).containsOnly(EMPLOYEE);
  }

  @Test
  void an_employee_does_not_see_the_bookings_of_another() throws Exception {
    createBooking("Nicht für andere");

    var page = json(post(OTHER_EMPLOYEE, LIST, Map.of("filter", listFilter(employeeIdOf(EMPLOYEE))))).at("/data/timereportList");

    assertThat(page.at("/items").size()).isZero();
    assertThat(page.at("/totalCount").asInt()).isZero();
  }

  @Test
  void without_a_person_in_the_filter_an_employee_sees_only_her_own() throws Exception {
    String id = createBooking("Eigene");

    var own = json(post(EMPLOYEE, LIST, Map.of("filter", listFilter(null)))).at("/data/timereportList");
    var other = json(post(OTHER_EMPLOYEE, LIST, Map.of("filter", listFilter(null)))).at("/data/timereportList");

    assertThat(ids(own)).contains(id);
    assertThat(own.at("/items").valueStream().map(item -> item.at("/employee/sign").asString())).containsOnly(EMPLOYEE);
    assertThat(ids(other)).doesNotContain(id);
  }

  /** The aspect guards a resolver as it guards a controller: the list asks for an unrestricted login. */
  @Test
  void a_restricted_login_gets_no_booking_list() throws Exception {
    var body = json(post(RESTRICTED, LIST, Map.of("filter", listFilter(null))));

    assertThat(body.at("/errors/0/extensions/classification").asString()).isEqualTo("FORBIDDEN");
    assertThat(body.at("/errors/0/extensions/code").asString()).isEqualTo("AA-0002");
    assertThat(body.at("/errors/0/message").asString()).isNotBlank();
  }

  @Test
  void a_page_larger_than_the_limit_is_refused() throws Exception {
    var filter = new HashMap<>(listFilter(null));
    filter.put("limit", 201);

    var body = json(post(MANAGER, LIST, Map.of("filter", filter)));

    assertThat(body.at("/errors/0/extensions/code").asString()).isEqualTo("GQ-0002");
    assertThat(body.at("/errors/0/extensions/classification").asString()).isEqualTo("BAD_REQUEST");
  }

  @Test
  void the_language_follows_accept_language() throws Exception {
    var filter = new HashMap<>(listFilter(null));
    filter.put("limit", 201);

    var german = json(post(MANAGER, LIST, Map.of("filter", filter), "de"));
    var english = json(post(MANAGER, LIST, Map.of("filter", filter), "en"));

    assertThat(german.at("/errors/0/message").asString()).isEqualTo("Die Seitengröße muss zwischen 1 und 200 liegen.");
    assertThat(english.at("/errors/0/message").asString()).isEqualTo("The page size must lie between 1 and 200.");
  }

  // --- booking ---

  @Test
  void create_read_and_update_a_booking() throws Exception {
    Map<String, Object> variables = createVariables(UUID.randomUUID().toString(), "PT1H30M", "Anlegen");

    var created = json(post(EMPLOYEE, CREATE, variables)).at("/data/createTimereport");
    assertThat(created.at("/replayed").asBoolean()).isFalse();
    assertThat(created.at("/timereport/duration").asString()).isEqualTo("PT1H30M");
    assertThat(created.at("/timereport/suborder/completeOrderSign").asString()).isEqualTo("ABC-12/01");
    String id = created.at("/timereport/id").asString();
    int version = created.at("/timereport/version").asInt();

    var own = json(post(EMPLOYEE, "query($day: Date!) { timereports(from: $day, until: $day) { id } }",
        Map.of("day", DAY.toString()))).at("/data/timereports");
    assertThat(own.valueStream().map(timereport -> timereport.at("/id").asString())).contains(id);

    var updated = json(post(EMPLOYEE, """
        mutation($id: ID!, $version: Int!) {
          updateTimereport(id: $id, version: $version, input: { duration: "PT2H" }) { timereport { duration taskDescription version } }
        }""", Map.of("id", id, "version", version))).at("/data/updateTimereport/timereport");
    assertThat(updated.at("/duration").asString()).isEqualTo("PT2H");
    assertThat(updated.at("/taskDescription").asString()).isEqualTo("Anlegen");
    assertThat(updated.at("/version").asInt()).isGreaterThan(version);
  }

  /** The version read is the version written against (concept 3.5): a change made in between is not overwritten. */
  @Test
  void a_change_to_a_booking_changed_since_it_was_read_is_refused() throws Exception {
    var created = json(post(EMPLOYEE, CREATE, createVariables(UUID.randomUUID().toString(), "PT1H", "Version")))
        .at("/data/createTimereport/timereport");
    String id = created.at("/id").asString();
    int version = created.at("/version").asInt();
    String update = """
        mutation($id: ID!, $version: Int!, $duration: Duration!) {
          updateTimereport(id: $id, version: $version, input: { duration: $duration }) { timereport { version } }
        }""";
    post(EMPLOYEE, update, Map.of("id", id, "version", version, "duration", "PT2H"));

    var stale = json(post(EMPLOYEE, update, Map.of("id", id, "version", version, "duration", "PT3H")));

    assertThat(stale.at("/errors/0/extensions/code").asString()).isEqualTo("XX-0003");
    assertThat(stale.at("/errors/0/extensions/classification").asString()).isEqualTo("CONFLICT");
  }

  @Test
  void a_repetition_with_the_same_key_creates_nothing() throws Exception {
    String key = UUID.randomUUID().toString();
    var first = json(post(EMPLOYEE, CREATE, createVariables(key, "PT1H", "Einmal"))).at("/data/createTimereport");

    var repeated = json(post(EMPLOYEE, CREATE, createVariables(key, "PT1H", "Einmal"))).at("/data/createTimereport");
    var otherInput = json(post(EMPLOYEE, CREATE, createVariables(key, "PT2H", "Einmal")));

    assertThat(repeated.at("/replayed").asBoolean()).isTrue();
    assertThat(repeated.at("/timereport/id").asString()).isEqualTo(first.at("/timereport/id").asString());
    assertThat(otherInput.at("/errors/0/extensions/code").asString()).isEqualTo("XX-0006");
    assertThat(otherInput.at("/errors/0/extensions/classification").asString()).isEqualTo("CONFLICT");
  }

  /** A key belongs to the login that used it: another person's same key creates her own booking. */
  @Test
  void a_key_belongs_to_its_login() throws Exception {
    String key = UUID.randomUUID().toString();
    json(post(EMPLOYEE, CREATE, createVariables(key, "PT1H", "Mein Schlüssel")));

    var other = json(post(OTHER_EMPLOYEE, CREATE, createVariables(key, "PT1H", "Mein Schlüssel")));

    // she has no employee order on the suborder: refused by the order module, not answered from the key
    assertThat(other.at("/errors/0/extensions/code").asString()).isEqualTo("TR-0003");
  }

  /** Not found and not readable look the same (concept 6.3). */
  @Test
  void the_booking_of_another_person_looks_like_none() throws Exception {
    String id = createBooking("Fremd");

    var body = json(post(OTHER_EMPLOYEE, "mutation($id: ID!) { deleteTimereport(id: $id) { id } }", Map.of("id", id)));

    assertThat(body.at("/errors/0/extensions/code").asString()).isEqualTo("TR-0001");
    assertThat(body.at("/errors/0/extensions/classification").asString()).isEqualTo("NOT_FOUND");
    assertThat(timereportRepository.findById(Long.parseLong(id))).isPresent();
  }

  /** The status rule of the period (#1164) answers through the service, with its business code. */
  @Test
  void the_status_rule_of_the_period_refuses_a_change() throws Exception {
    var created = json(post(EMPLOYEE, CREATE, createVariables(UUID.randomUUID().toString(), "PT1H", "Freigegeben")))
        .at("/data/createTimereport/timereport");
    String id = created.at("/id").asString();
    // what a release does to the booking
    var timereport = timereportRepository.findById(Long.parseLong(id)).orElseThrow();
    timereport.setStatus(GlobalConstants.TIMEREPORT_STATUS_COMMITTED);
    timereport = timereportRepository.save(timereport);

    var refused = json(post(EMPLOYEE, """
        mutation($id: ID!, $version: Int!) { updateTimereport(id: $id, version: $version, input: { duration: "PT3H" }) { timereport { id } } }""",
        Map.of("id", id, "version", timereport.getUpdatecounter())));

    assertThat(refused.at("/errors/0/extensions/code").asString()).startsWith("TR-");
    assertThat(refused.at("/errors/0/message").asString()).isNotBlank();
  }

  /**
   * Moving a booking to another day takes the employee order valid there, as the page does on every save: here the
   * person's employee order on the suborder is split into two periods, and the new day lies in the second.
   */
  @Test
  void a_booking_moved_to_another_day_takes_the_employee_order_valid_there() throws Exception {
    LocalDate firstDay = DAY.minusWeeks(3);
    LocalDate nextDay = firstDay.plusDays(1);
    var contract = contractOf(MOVER, EMPLOYEE_STATUS_MA);
    var suborder = suborderRepository.findById(suborderId).orElseThrow();
    var first = new Employeeorder();
    first.setSuborder(suborder);
    first.setEmployeecontract(contract);
    first.setFromDate(LocalDate.of(2000, 1, 1));
    first.setUntilDate(firstDay);
    employeeorderRepository.save(first);
    var second = new Employeeorder();
    second.setSuborder(suborder);
    second.setEmployeecontract(contract);
    second.setFromDate(nextDay);
    long secondId = employeeorderRepository.save(second).getId();
    for (LocalDate day : List.of(firstDay, nextDay)) {
      post(MOVER, "mutation($day: Date!) { upsertWorkingday(input: { date: $day, startTime: \"08:00\" }) { date } }",
          Map.of("day", day.toString()));
    }
    Map<String, Object> variables = new HashMap<>(createVariables(UUID.randomUUID().toString(), "PT1H", "Verschieben"));
    variables.put("day", firstDay.toString());
    var created = json(post(MOVER, CREATE, variables)).at("/data/createTimereport/timereport");

    var moved = json(post(MOVER, """
        mutation($id: ID!, $version: Int!, $day: Date!) {
          updateTimereport(id: $id, version: $version, input: { date: $day }) { timereport { date employeeorderId } }
        }""", Map.of("id", created.at("/id").asString(), "version", created.at("/version").asInt(),
        "day", nextDay.toString())));

    assertThat(moved.at("/errors").isMissingNode()).as(moved.toString()).isTrue();
    assertThat(moved.at("/data/updateTimereport/timereport/date").asString()).isEqualTo(nextDay.toString());
    assertThat(moved.at("/data/updateTimereport/timereport/employeeorderId").asString()).isEqualTo(String.valueOf(secondId));
  }

  // --- working day ---

  /** Without a start of work there is no booking (TR-0024); the interface can enter it itself. */
  @Test
  void a_new_day_needs_its_start_of_work_first() throws Exception {
    LocalDate day = DAY.minusDays(1);
    Map<String, Object> variables = new HashMap<>(createVariables(UUID.randomUUID().toString(), "PT1H", "Neuer Tag"));
    variables.put("day", day.toString());
    var before = json(post(EMPLOYEE, CREATE, variables));
    assertThat(before.at("/errors/0/extensions/code").asString()).isEqualTo("TR-0024");

    var workingday = json(post(EMPLOYEE, """
        mutation($day: Date!) { upsertWorkingday(input: { date: $day, startTime: "07:30", breakLength: "PT30M" }) {
          date startTime breakLength type } }""", Map.of("day", day.toString()))).at("/data/upsertWorkingday");
    assertThat(workingday.at("/startTime").asString()).isEqualTo("07:30");
    assertThat(workingday.at("/breakLength").asString()).isEqualTo("PT30M");
    assertThat(workingday.at("/type").asString()).isEqualTo("WORKED");

    var after = json(post(EMPLOYEE, CREATE, variables)).at("/data/createTimereport/timereport/id");
    assertThat(after.isMissingNode() || after.isNull()).isFalse();
  }

  @Test
  void a_day_without_bookings_can_be_marked_as_not_worked() throws Exception {
    LocalDate day = DAY.minusDays(2);

    var marked = json(post(EMPLOYEE, "mutation($day: Date!) { markNotWorked(date: $day) { type startTime } }",
        Map.of("day", day.toString()))).at("/data/markNotWorked");
    var read = json(post(EMPLOYEE, "query($day: Date!) { workingday(date: $day) { type } }",
        Map.of("day", day.toString()))).at("/data/workingday");

    assertThat(marked.at("/type").asString()).isEqualTo("NOT_WORKED");
    assertThat(read.at("/type").asString()).isEqualTo("NOT_WORKED");
  }

  // --- helpers ---

  private Map<String, Object> createVariables(String key, String duration, String text) {
    return Map.of("key", key, "suborder", suborderId, "day", DAY.toString(), "duration", duration, "text", text);
  }

  private String createBooking(String description) throws Exception {
    var body = json(post(EMPLOYEE, CREATE, createVariables(UUID.randomUUID().toString(), "PT30M", description)));
    return body.at("/data/createTimereport/timereport/id").asString();
  }

  private Map<String, Object> listFilter(Long employeeId) {
    return employeeId == null
        ? Map.of("from", DAY.toString(), "until", DAY.toString())
        : Map.of("from", DAY.toString(), "until", DAY.toString(), "employeeIds", List.of(employeeId));
  }

  private long employeeIdOf(String sign) {
    return employeeRepository.findBySign(sign).orElseThrow().getId();
  }

  private static List<String> ids(JsonNode page) {
    return page.at("/items").valueStream().map(item -> item.at("/id").asString()).toList();
  }

  private HttpResponse<String> post(String login, String query) throws Exception {
    return post(login, query, Map.of());
  }

  private HttpResponse<String> post(String login, String query, Map<String, Object> variables) throws Exception {
    return post(login, query, variables, "de");
  }

  private HttpResponse<String> post(String login, String query, Map<String, Object> variables, String language)
      throws Exception {
    String body = JSON.writeValueAsString(Map.of("query", query, "variables", variables));
    var request = HttpRequest.newBuilder(uri("/api/graphql", login))
        .header("Content-Type", "application/json")
        .header("Accept-Language", language)
        .POST(BodyPublishers.ofString(body))
        .build();
    return HttpClient.newHttpClient().send(request, BodyHandlers.ofString());
  }

  private HttpResponse<String> get(String login, String path) throws Exception {
    return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri(path, login)).GET().build(), BodyHandlers.ofString());
  }

  private URI uri(String path, String login) {
    return URI.create("http://localhost:" + port + path + "?login-name=" + login);
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
