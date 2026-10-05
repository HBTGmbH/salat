package de.hbt.salat.order.service;

import static de.hbt.salat.testutils.CustomerTestUtils.uniqueShortname;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.command.CommandPublisher;
import de.hbt.salat.common.event.SignsRenamedEvent;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.customer.persistence.CustomerDAO;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.persistence.EmployeeDAO;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.CustomerorderDTO;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.SuborderDTO;
import de.hbt.salat.order.domain.TicketReferenceMode;
import de.hbt.salat.order.persistence.CustomerorderDAO;
import de.hbt.salat.order.persistence.SuborderDAO;
import de.hbt.salat.order.persistence.SuborderRepository;
import de.hbt.salat.testutils.EmployeeTestUtils;

/**
 * The complete order sign is stored with every suborder (#1342), and the order module keeps it in step
 * with the tree: on creating a suborder, on renaming or moving one — for its whole branch — and on
 * renaming the order. After each change the stored sign of every suborder equals the one its parent
 * chain spells, and the suborders the change does not touch keep theirs.
 */
@DataJpaTest
@RecordApplicationEvents
@DisplayNameGeneration(ReplaceUnderscores.class)
@Import({AuthorizedUserAuditorAware.class, SalatProperties.class, SuborderService.class, SuborderDAO.class,
    CustomerorderService.class, CustomerorderDAO.class, CustomerDAO.class, CommandPublisher.class,
    SpecialOrders.class})
class StoredCompleteOrderSignTest {

  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final LocalDate UNTIL = LocalDate.of(2026, 12, 31);

  @Autowired
  private SuborderService suborderService;

  @Autowired
  private CustomerorderService customerorderService;

  @Autowired
  private SuborderRepository suborderRepository;

  @Autowired
  private TestEntityManager entityManager;

  @Autowired
  private ApplicationEvents events;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  /** The responsible person of an order is all the order needs from the employee module here. */
  @MockitoBean
  private EmployeeDAO employeeDAO;

  private Employee responsible;
  private Customerorder order;
  private Customerorder otherOrder;
  private Suborder first;
  private Suborder branch;
  private Suborder leaf;
  private Suborder second;
  private Suborder foreign;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.isManager()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");

    responsible = EmployeeTestUtils.createEmployee("rsp");
    entityManager.persist(responsible.getSalatUser());
    entityManager.persist(responsible);
    when(employeeDAO.getEmployeeById(responsible.getId())).thenReturn(responsible);
    var customer = customer();
    order = customerorder(customer, "CO");
    otherOrder = customerorder(customer, "OTHER");
    first = suborder(order, null, "01");
    branch = suborder(order, first, "A");
    leaf = suborder(order, branch, "x");
    second = suborder(order, null, "02");
    foreign = suborder(otherOrder, null, "01");
    entityManager.flush();
  }

  @Test
  void a_new_suborder_stores_the_sign_of_its_place_in_the_tree() {
    suborderService.create(dto("y", branch.getId()), order.getId());

    assertThat(storedSigns()).containsEntry(signOf("y"), "CO/01/A/y");
    assertEveryStoredSignFollowsItsChain();
  }

  @Test
  void a_renamed_order_moves_the_signs_of_all_its_suborders_along() {
    var unchanged = storedSigns(foreign);

    customerorderService.update(order.getId(), customerorderDto(order, "NEW"));

    assertThat(storedSigns(first, branch, leaf, second)).containsOnly(
        Map.entry(first.getId(), "NEW/01"), Map.entry(branch.getId(), "NEW/01/A"),
        Map.entry(leaf.getId(), "NEW/01/A/x"), Map.entry(second.getId(), "NEW/02"));
    assertThat(storedSigns(foreign)).isEqualTo(unchanged);
    assertEveryStoredSignFollowsItsChain();
  }

  /**
   * The order's own rename covers its suborders. Their validity is adjusted on every change of the
   * order, and with a sign still stored under the old name each would count as renamed itself.
   */
  @Test
  void a_renamed_order_is_announced_once_and_not_once_per_suborder() {
    customerorderService.update(order.getId(), customerorderDto(order, "NEW"));

    assertThat(events.stream(SignsRenamedEvent.class))
        .extracting(SignsRenamedEvent::getOldSign, SignsRenamedEvent::getNewSign)
        .containsExactly(tuple("CO", "NEW"));
  }

  @Test
  void a_renamed_suborder_moves_the_signs_of_its_branch_along() {
    var unchanged = storedSigns(second, foreign);

    suborderService.update(first.getId(), dto("10", null), order.getId());

    assertThat(storedSigns(first, branch, leaf)).containsOnly(
        Map.entry(first.getId(), "CO/10"), Map.entry(branch.getId(), "CO/10/A"),
        Map.entry(leaf.getId(), "CO/10/A/x"));
    assertThat(storedSigns(second, foreign)).isEqualTo(unchanged);
    assertEveryStoredSignFollowsItsChain();
  }

  @Test
  void a_moved_branch_takes_its_signs_to_the_new_parent() {
    var unchanged = storedSigns(first, second, foreign);

    suborderService.update(branch.getId(), dto("A", second.getId()), order.getId());

    assertThat(storedSigns(branch, leaf)).containsOnly(
        Map.entry(branch.getId(), "CO/02/A"), Map.entry(leaf.getId(), "CO/02/A/x"));
    assertThat(storedSigns(first, second, foreign)).isEqualTo(unchanged);
    assertEveryStoredSignFollowsItsChain();
  }

  @Test
  void a_branch_moved_into_another_order_takes_its_signs_along() {
    var unchanged = storedSigns(second, foreign);

    suborderService.update(first.getId(), dto("01", foreign.getId()), otherOrder.getId());

    assertThat(storedSigns(first, branch, leaf)).containsOnly(
        Map.entry(first.getId(), "OTHER/01/01"), Map.entry(branch.getId(), "OTHER/01/01/A"),
        Map.entry(leaf.getId(), "OTHER/01/01/A/x"));
    assertThat(storedSigns(second, foreign)).isEqualTo(unchanged);
    assertEveryStoredSignFollowsItsChain();
  }

  /** What is in the database, not what the session still holds. */
  private Map<Long, String> storedSigns(Suborder... suborders) {
    var all = storedSigns();
    var result = new HashMap<Long, String>();
    Arrays.stream(suborders).map(Suborder::getId).forEach(id -> result.put(id, all.get(id)));
    return result;
  }

  private Map<Long, String> storedSigns() {
    entityManager.flush();
    entityManager.clear();
    var result = new HashMap<Long, String>();
    suborderRepository.findAll().forEach(suborder -> result.put(suborder.getId(), suborder.getCompleteOrderSign()));
    return result;
  }

  private Long signOf(String sign) {
    return StreamSupport.stream(suborderRepository.findAll().spliterator(), false)
        .filter(suborder -> sign.equals(suborder.getSign()))
        .map(Suborder::getId)
        .findFirst()
        .orElseThrow();
  }

  private void assertEveryStoredSignFollowsItsChain() {
    entityManager.flush();
    entityManager.clear();
    for (var suborder : suborderRepository.findAll()) {
      assertThat(suborder.getCompleteOrderSign()).as("suborder %s", suborder.getId()).isEqualTo(chainOf(suborder));
    }
  }

  /** The complete sign as the parent chain spells it — what every reader used to build. */
  private static String chainOf(Suborder suborder) {
    var signs = new ArrayList<String>();
    for (var current = suborder; current != null; current = current.getParentorder()) {
      signs.addFirst(current.getSign());
    }
    return suborder.getCustomerorder().getSign() + "/" + String.join("/", signs);
  }

  private static SuborderDTO dto(String sign, Long parentId) {
    return new SuborderDTO(null, sign, "Leistung", null, null, GlobalConstants.INVOICE_YES, false, false, false,
        false, null, DateUtils.format(FROM), DateUtils.format(UNTIL), null, null, false, parentId, null, null);
  }

  private CustomerorderDTO customerorderDto(Customerorder customerorder, String sign) {
    return new CustomerorderDTO(customerorder.getCustomer().getId(), FROM, UNTIL, sign,
        customerorder.getDescription(), null, null, null, null, List.of(responsible.getId()), responsible.getId(),
        null, null, false, OrderType.STANDARD, TicketReferenceMode.UNLIMITED, null);
  }

  private Customer customer() {
    var created = new Customer();
    created.setShortname(uniqueShortname("cust"));
    created.setName("Customer");
    created.setAddress("Teststraße 1");
    return entityManager.persist(created);
  }

  private Customerorder customerorder(Customer customer, String sign) {
    var customerorder = new Customerorder();
    customerorder.setCustomer(customer);
    customerorder.setSign(sign);
    customerorder.setDescription("Auftrag " + sign);
    customerorder.setFromDate(FROM);
    customerorder.setUntilDate(UNTIL);
    customerorder.setOrderType(OrderType.STANDARD);
    customerorder.setDebithours(Duration.ZERO);
    return entityManager.persist(customerorder);
  }

  private Suborder suborder(Customerorder customerorder, Suborder parent, String sign) {
    var suborder = new Suborder();
    suborder.setCustomerorder(customerorder);
    suborder.setParentorder(parent);
    suborder.setSign(sign);
    suborder.setDescription("Leistung");
    suborder.setFromDate(FROM);
    suborder.setUntilDate(UNTIL);
    suborder.setDebithours(Duration.ZERO);
    if (parent != null) {
      parent.addSuborder(suborder);
    }
    return entityManager.persist(suborder);
  }
}
