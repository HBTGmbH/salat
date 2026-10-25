package de.hbt.salat.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.order.auth.EmployeeorderAuthorization;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.SearchHits;
import de.hbt.salat.order.domain.Suborder;

/**
 * The order, suborder and employee order lists show something without a filter and therefore ask the database for
 * their first rows and the number of all hits (#1380). The search text, which used to be applied to the loaded rows in
 * Java, is part of that query now and must still find what the list shows.
 */
@DataJpaTest
@Import({AuthorizedUserAuditorAware.class, CustomerorderDAO.class, SuborderDAO.class, EmployeeorderDAO.class,
    EmployeeorderAuthorization.class})
@DisplayNameGeneration(ReplaceUnderscores.class)
class OrderListSearchTest {

  private static final LocalDate FROM = LocalDate.of(2020, 1, 1);

  @Autowired
  private CustomerorderDAO customerorderDAO;

  @Autowired
  private SuborderDAO suborderDAO;

  @Autowired
  private EmployeeorderDAO employeeorderDAO;

  @Autowired
  private TestEntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  private Customer customer;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
    when(authorizedUser.isManager()).thenReturn(true);
    customer = customer("ACME", "Acme Werke");
  }

  // --- Aufträge ---------------------------------------------------------------------------------

  @Test
  void the_order_list_shows_the_first_rows_by_sign_and_counts_every_hit() {
    order("c-3");
    order("B-2");
    order("a-1");

    var hits = customerorderDAO.findCustomerorders(null, null, null, null, List.of(), 2);

    assertThat(hits.rows()).extracting(Customerorder::getSign).containsExactly("a-1", "B-2");
    assertThat(hits.total()).isEqualTo(3);
    assertThat(hits.truncated()).isTrue();
  }

  @Test
  void the_order_list_finds_an_order_by_the_name_of_a_responsible_employee() {
    var order = order("ORD-1");
    order.setResponsibleHbt(List.of(contract("abc", "Erika", "Muster").getEmployee()));
    order("ORD-2");
    entityManager.flush();

    assertThat(signs(customerorderDAO.findCustomerorders(null, "erika mus", null, null, List.of(), 500)))
        .containsExactly("ORD-1");
  }

  @Test
  void an_order_with_two_responsible_employees_is_one_hit() {
    var order = order("ORD-1");
    order.setResponsibleHbt(List.of(contract("abc", "Erika", "Muster").getEmployee(),
        contract("def", "Max", "Muster").getEmployee()));
    entityManager.flush();

    var hits = customerorderDAO.findCustomerorders(null, "muster", null, null, List.of(), 500);

    assertThat(signs(hits)).containsExactly("ORD-1");
    assertThat(hits.total()).isEqualTo(1);
  }

  /** A customer stands for the orders it placed as intermediary as well (#1536), in every filter by customer. */
  @Test
  void the_filter_by_customer_finds_the_orders_it_mediates() {
    var intermediary = customer("VM", "Vermittlung");
    intermediary.setIntermediary(true);
    var mediated = order("ORD-1");
    mediated.setIntermediary(intermediary);
    order("ORD-2");
    entityManager.flush();

    assertThat(signs(customerorderDAO.findCustomerorders(null, null, intermediary.getId(), null, List.of(), 500)))
        .containsExactly("ORD-1");
    assertThat(customerorderDAO.getCustomerordersByFilters(null, null, intermediary.getId(), null))
        .extracting(Customerorder::getSign).containsExactly("ORD-1");
    assertThat(signs(customerorderDAO.findCustomerorders(null, null, customer.getId(), null, List.of(), 500)))
        .containsExactly("ORD-1", "ORD-2");
  }

  /** The suborder and employee order lists filter by customer the same way (#1536). */
  @Test
  void the_suborder_and_employee_order_lists_find_what_the_customer_mediates() {
    var intermediary = customer("VM", "Vermittlung");
    intermediary.setIntermediary(true);
    var mediated = order("ORD-1");
    mediated.setIntermediary(intermediary);
    var mediatedSuborder = suborder(mediated, null, "01", "Betrieb", null);
    var direct = suborder(order("ORD-2"), null, "01", "Betrieb", null);
    var contract = contract("abc", "Erika", "Muster");
    employeeorder(contract, mediatedSuborder, FROM);
    employeeorder(contract, direct, FROM);
    entityManager.flush();

    assertThat(completeSigns(suborderDAO.findSuborders(null, null, null, intermediary.getId(), null, List.of(), 500)))
        .containsExactly("ORD-1/01");
    assertThat(employeeorderDAO.findEmployeeorders(true, null, null, intermediary.getId(), null, null, null, 500)
        .rows()).extracting(OrderListSearchTest::label).containsExactly("ORD-1/01 2020-01-01 abc");
  }

  @Test
  void the_order_list_finds_an_order_by_its_customer() {
    order("ORD-1");
    var other = new Customerorder();
    other.setCustomer(customer("OTHER", "Andere GmbH"));
    other.setSign("ORD-2");
    other.setDescription("Wartung");
    other.setFromDate(FROM);
    other.setOrderType(OrderType.STANDARD);
    other.setDebithours(Duration.ZERO);
    entityManager.persist(other);

    assertThat(signs(customerorderDAO.findCustomerorders(null, "acme werke", null, null, List.of(), 500)))
        .containsExactly("ORD-1");
  }

  /** {@code %} and {@code _} in the search text stand for themselves, as they did in the search in Java. */
  @Test
  void a_wildcard_in_the_search_text_is_taken_literally() {
    order("ORD_1");
    order("ORDX1");

    assertThat(signs(customerorderDAO.findCustomerorders(null, "d_1", null, null, List.of(), 500))).containsExactly("ORD_1");
  }

  @Test
  void the_switches_still_widen_the_order_list() {
    order("RUNNING");
    order("ENDED").setUntilDate(LocalDate.of(2021, 1, 1));
    order("HIDDEN").setHide(true);
    entityManager.flush();

    assertThat(signs(customerorderDAO.findCustomerorders(false, null, null, false, List.of(), 500))).containsExactly("RUNNING");
    assertThat(signs(customerorderDAO.findCustomerorders(true, null, null, true, List.of(), 500)))
        .containsExactly("ENDED", "HIDDEN", "RUNNING");
  }

  // --- Unteraufträge ----------------------------------------------------------------------------

  @Test
  void the_suborder_list_shows_the_first_rows_by_complete_sign_and_counts_every_hit() {
    var order = order("ORD");
    var top = suborder(order, null, "02", "Betrieb", null);
    suborder(order, top, "01", "Wartung", null);
    suborder(order, null, "01", "Entwicklung", null);

    var hits = suborderDAO.findSuborders(null, null, null, null, null, List.of(), 2);

    assertThat(hits.rows()).extracting(Suborder::getCompleteOrderSign).containsExactly("ORD/01", "ORD/02");
    assertThat(hits.total()).isEqualTo(3);
  }

  /**
   * The list shows the descriptions of every suborder above a row ({@link Suborder#getCompleteOrderDescription}), and
   * the search in Java found a row by them — so does the query.
   */
  @Test
  void the_suborder_list_finds_a_suborder_by_the_description_of_one_above_it() {
    var order = order("ORD");
    var top = suborder(order, null, "01", "Betrieb", null);
    var middle = suborder(order, top, "01", null, "Wartung der Anlagen");
    suborder(order, middle, "01", "Rufbereitschaft", null);
    suborder(order, null, "02", "Entwicklung", null);

    assertThat(completeSigns(suborderDAO.findSuborders(null, "wartung", null, null, null, List.of(), 500)))
        .containsExactly("ORD/01/01", "ORD/01/01/01");
  }

  /** {@code ORD/0_} would be a pattern for {@code ORD/01} — the relation compares the sign as it is. */
  @Test
  void a_suborder_with_an_underscore_in_its_sign_is_no_parent_of_its_namesakes() {
    var order = order("ORD");
    suborder(order, null, "0_", "Wartung", null);
    suborder(order, suborder(order, null, "01", "Betrieb", null), "01", "Rufbereitschaft", null);

    assertThat(completeSigns(suborderDAO.findSuborders(null, "wartung", null, null, null, List.of(), 500)))
        .containsExactly("ORD/0_");
  }

  @Test
  void the_suborder_list_finds_a_suborder_by_its_order_and_customer() {
    var order = order("ORD");
    suborder(order, null, "01", "Betrieb", null);

    assertThat(completeSigns(suborderDAO.findSuborders(null, "acme", null, null, null, List.of(), 500)))
        .containsExactly("ORD/01");
    assertThat(completeSigns(suborderDAO.findSuborders(null, "beschreibung ord", null, null, null, List.of(), 500)))
        .containsExactly("ORD/01");
  }

  @Test
  void the_suborder_list_finds_a_suborder_by_its_long_description() {
    var order = order("ORD");
    suborder(order, null, "01", "Betrieb", "Betrieb der Rechenzentren");
    suborder(order, null, "02", "Betrieb", "Betrieb der Arbeitsplätze");

    assertThat(completeSigns(suborderDAO.findSuborders(null, "rechenzentren", null, null, null, List.of(), 500)))
        .containsExactly("ORD/01");
  }

  // --- Mitarbeiteraufträge ----------------------------------------------------------------------

  /** By order, suborder and begin — the employee only decides between equal ones. */
  @Test
  void the_employee_order_list_is_ordered_by_order_suborder_and_begin() {
    var first = suborder(order("A"), null, "01", "Betrieb", null);
    var second = suborder(order("B"), null, "01", "Betrieb", null);
    var anna = contract("aaa", "Anna", "Alpha");
    var zoe = contract("zzz", "Zoe", "Zeta");
    employeeorder(zoe, second, FROM);
    employeeorder(anna, second, FROM);
    employeeorder(zoe, first, FROM.plusYears(1));
    employeeorder(anna, first, FROM.plusYears(2));

    var hits = employeeorderDAO.findEmployeeorders(true, null, null, null, null, null, null, 3);

    assertThat(hits.rows()).extracting(OrderListSearchTest::label)
        .containsExactly("A/01 2021-01-01 zzz", "A/01 2022-01-01 aaa", "B/01 2020-01-01 aaa");
    assertThat(hits.total()).isEqualTo(4);
  }

  @Test
  void the_employee_order_list_finds_an_order_by_the_employee_and_by_the_suborder() {
    var suborder = suborder(order("ORD"), null, "01", "Betrieb", null);
    var other = suborder(order("OTHER"), null, "01", "Wartung", null);
    employeeorder(contract("abc", "Erika", "Muster"), suborder, FROM);
    employeeorder(contract("def", "Max", "Beispiel"), other, FROM);

    assertThat(employeeorderDAO.findEmployeeorders(true, "muster", null, null, null, null, null, 500).rows())
        .extracting(OrderListSearchTest::label).containsExactly("ORD/01 2020-01-01 abc");
    assertThat(employeeorderDAO.findEmployeeorders(true, "wartung", null, null, null, null, null, 500).rows())
        .extracting(OrderListSearchTest::label).containsExactly("OTHER/01 2020-01-01 def");
  }

  // --- Aufbau -----------------------------------------------------------------------------------

  private static List<String> signs(SearchHits<Customerorder> hits) {
    return hits.rows().stream().map(Customerorder::getSign).toList();
  }

  private static List<String> completeSigns(SearchHits<Suborder> hits) {
    return hits.rows().stream().map(Suborder::getCompleteOrderSign).toList();
  }

  private static String label(Employeeorder employeeorder) {
    return employeeorder.getSuborder().getCompleteOrderSign() + " " + employeeorder.getFromDate() + " "
        + employeeorder.getEmployeecontract().getEmployee().getSign();
  }

  private Customer customer(String shortname, String name) {
    var created = new Customer();
    created.setShortname(shortname);
    created.setName(name);
    created.setAddress("Teststraße 1");
    return entityManager.persist(created);
  }

  private Customerorder order(String sign) {
    var order = new Customerorder();
    order.setCustomer(customer);
    order.setSign(sign);
    order.setDescription("Beschreibung " + sign);
    order.setFromDate(FROM);
    order.setOrderType(OrderType.STANDARD);
    order.setDebithours(Duration.ZERO);
    return entityManager.persist(order);
  }

  private Suborder suborder(Customerorder order, Suborder parent, String sign, String shortdescription,
      String description) {
    var suborder = new Suborder();
    suborder.setCustomerorder(order);
    suborder.setParentorder(parent);
    suborder.setSign(sign);
    suborder.setShortdescription(shortdescription);
    suborder.setDescription(description != null ? description : "Unterauftrag");
    suborder.setFromDate(FROM);
    suborder.setDebithours(Duration.ZERO);
    return entityManager.persist(suborder);
  }

  private void employeeorder(Employeecontract contract, Suborder suborder, LocalDate from) {
    var employeeorder = new Employeeorder();
    employeeorder.setEmployeecontract(contract);
    employeeorder.setSuborder(suborder);
    employeeorder.setFromDate(from);
    employeeorder.setDebithours(Duration.ZERO);
    entityManager.persist(employeeorder);
    entityManager.flush();
  }

  private Employeecontract contract(String sign, String firstname, String lastname) {
    var user = new SalatUser();
    user.setLoginname(sign);
    user.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
    entityManager.persist(user);

    var employee = new Employee();
    employee.setSign(sign);
    employee.setFirstname(firstname);
    employee.setLastname(lastname);
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setHide(false);
    employee.setSalatUser(user);
    entityManager.persist(employee);

    var created = new Employeecontract();
    created.setEmployee(employee);
    created.setValidFrom(FROM);
    created.setDailyWorkingTime(Duration.ofHours(8));
    created.setOvertimeStatic(Duration.ZERO);
    created.setHide(false);
    return entityManager.persist(created);
  }
}
