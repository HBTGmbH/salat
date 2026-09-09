package org.tb.order.persistence;

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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.persistence.AuthorizedUserAuditorAware;
import org.tb.common.GlobalConstants;
import org.tb.customer.domain.Customer;
import org.tb.customer.domain.CustomerSegment;
import org.tb.customer.persistence.CustomerRepository;
import org.tb.customer.persistence.CustomerSegmentRepository;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeRepository;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.OrderType;

/**
 * The responsible employees offered in the budget dashboard filter (#920). Select boxes leave out
 * hidden records: a responsibility on a hidden order has expired with the order, and a hidden
 * employee is not to be offered at all. The signs used for the actual filtering are deliberately
 * not restricted this way — the unfiltered dashboard lists plans of hidden orders too.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
public class CustomerorderRepositoryTest {

  @Autowired
  private CustomerorderRepository customerorderRepository;

  @Autowired
  private CustomerRepository customerRepository;

  @Autowired
  private CustomerSegmentRepository customerSegmentRepository;

  @Autowired
  private EmployeeRepository employeeRepository;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  private Customer customer;

  @BeforeEach
  public void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
    customer = customer();
  }

  @Test
  public void offers_the_responsible_of_a_visible_order() {
    var employee = employee("aaa");
    order("co-visible", false, employee);

    assertThat(signsOfVisibleResponsibles()).containsExactly("aaa");
  }

  @Test
  public void leaves_out_someone_who_is_only_responsible_for_a_hidden_order() {
    var employee = employee("bbb");
    order("co-hidden", true, employee);

    assertThat(signsOfVisibleResponsibles()).isEmpty();
  }

  @Test
  public void leaves_out_a_hidden_employee() {
    var employee = employee("ccc");
    employee.setHide(true);
    employeeRepository.save(employee);
    order("co-visible", false, employee);

    assertThat(signsOfVisibleResponsibles()).isEmpty();
  }

  @Test
  public void keeps_someone_who_is_responsible_for_a_hidden_and_a_visible_order() {
    var employee = employee("ddd");
    order("co-hidden", true, employee);
    order("co-visible", false, employee);

    assertThat(signsOfVisibleResponsibles()).containsExactly("ddd");
  }

  /** Several orders must not multiply the entry — the filter offers each person once. */
  @Test
  public void names_someone_responsible_for_several_orders_only_once() {
    var employee = employee("eee");
    order("co-one", false, employee);
    order("co-two", false, employee);

    assertThat(signsOfVisibleResponsibles()).containsExactly("eee");
  }

  @Test
  public void orders_the_choices_by_sign() {
    order("co-one", false, employee("zzz"));
    order("co-two", false, employee("mmm"));
    order("co-three", false, employee("aaa"));

    assertThat(signsOfVisibleResponsibles()).containsExactly("aaa", "mmm", "zzz");
  }

  // --- narrowed to one customer segment (#952) --------------------------------------------------

  /**
   * With a segment chosen, the filter offers only the responsibles of that segment's orders —
   * otherwise the two dashboard filters can be combined into a selection no order can match.
   */
  @Test
  public void offers_only_the_responsibles_of_the_chosen_segment() {
    var segment = segment("Segment A");
    var otherSegment = segment("Segment B");
    order("co-a", false, customerIn(segment), employee("aaa"));
    order("co-b", false, customerIn(otherSegment), employee("bbb"));

    assertThat(signsOfResponsiblesInSegment(segment.getId())).containsExactly("aaa");
  }

  @Test
  public void leaves_out_a_responsible_whose_only_order_in_the_segment_is_hidden() {
    var segment = segment("Segment C");
    order("co-hidden", true, customerIn(segment), employee("ccc"));

    assertThat(signsOfResponsiblesInSegment(segment.getId())).isEmpty();
  }

  @Test
  public void names_a_responsible_of_several_orders_in_the_segment_only_once() {
    var segment = segment("Segment D");
    var customer = customerIn(segment);
    var employee = employee("ddd");
    order("co-one", false, customer, employee);
    order("co-two", false, customer, employee);

    assertThat(signsOfResponsiblesInSegment(segment.getId())).containsExactly("ddd");
  }

  /** A customer without a segment belongs to no segment — not to every one of them. */
  @Test
  public void leaves_out_a_responsible_whose_customer_has_no_segment() {
    var segment = segment("Segment E");
    order("co-without-segment", false, customer, employee("eee"));

    assertThat(signsOfResponsiblesInSegment(segment.getId())).isEmpty();
  }

  private List<String> signsOfVisibleResponsibles() {
    return customerorderRepository.findAllVisibleResponsibleHbt().stream()
        .map(Employee::getSign)
        .toList();
  }

  private List<String> signsOfResponsiblesInSegment(long segmentId) {
    return customerorderRepository.findVisibleResponsibleHbtByCustomerSegmentId(segmentId).stream()
        .map(Employee::getSign)
        .toList();
  }

  private CustomerSegment segment(String name) {
    var created = new CustomerSegment();
    created.setName(name);
    return customerSegmentRepository.save(created);
  }

  private Customer customerIn(CustomerSegment segment) {
    var created = customer();
    created.setSegment(segment);
    return customerRepository.save(created);
  }

  private Customer customer() {
    var created = new Customer();
    created.setName("Testkunde");
    created.setShortname("TK");
    created.setAddress("Teststraße 1");
    return customerRepository.save(created);
  }

  /**
   * {@code hide} is set explicitly because the schema defaults the column to {@code false} while a
   * fresh entity leaves it {@code null} — and {@code hide != true} drops a {@code null} row, here as
   * in every other select box query of the application ({@code EmployeeDAO.notHidden()}).
   */
  private Employee employee(String sign) {
    var employee = new Employee();
    employee.setSign(sign);
    employee.setFirstname("Vorname");
    employee.setLastname("Nachname");
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setHide(false);
    return employeeRepository.save(employee);
  }

  private void order(String sign, boolean hidden, Employee... responsibles) {
    order(sign, hidden, customer, responsibles);
  }

  private void order(String sign, boolean hidden, Customer orderCustomer, Employee... responsibles) {
    var order = new Customerorder();
    order.setCustomer(orderCustomer);
    order.setSign(sign);
    order.setDescription(sign);
    order.setFromDate(LocalDate.of(2026, 1, 1));
    order.setOrderType(OrderType.STANDARD);
    order.setDebithours(Duration.ZERO);
    order.setHide(hidden);
    order.setResponsibleHbt(List.of(responsibles));
    customerorderRepository.save(order);
  }
}
