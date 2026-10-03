package de.hbt.salat.order.persistence;

import static de.hbt.salat.common.GlobalConstants.YESNO_NO;
import static de.hbt.salat.common.GlobalConstants.YESNO_YES;
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
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.CustomerorderOption;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.Suborder;

/**
 * The orders the invoice page offers (#1283): every order with at least one invoiceable suborder,
 * once, ordered by sign, read as plain values together with its customer — no suborder and no
 * association of the order comes along.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class InvoiceableCustomerordersTest {

  @Autowired
  private CustomerorderRepository customerorderRepository;

  @Autowired
  private TestEntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  private Customer customer;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
    customer = customer("BSP", "Beispielkunde");
  }

  @Test
  void offers_an_order_with_an_invoiceable_suborder() {
    var order = order(customer, "MUSTER-01", "Plattform", false);
    suborder(order, "01", YESNO_YES);

    assertThat(customerorderRepository.findAllInvoiceable())
        .containsExactly(new CustomerorderOption(order.getId(), "MUSTER-01", "Plattform", "Vorgang", "BSP", "Beispielkunde", false));
  }

  @Test
  void offers_an_order_with_several_invoiceable_suborders_once() {
    var order = order(customer, "MUSTER-01", null, false);
    suborder(order, "01", YESNO_YES);
    suborder(order, "02", YESNO_YES);
    suborder(order, "03", YESNO_NO);

    assertThat(signs()).containsExactly("MUSTER-01");
  }

  @Test
  void leaves_out_an_order_whose_suborders_are_not_invoiceable() {
    suborder(order(customer, "INTERN-01", null, false), "01", YESNO_NO);
    order(customer, "LEER-01", null, false);

    assertThat(signs()).isEmpty();
  }

  /** Hidden orders stay in the choice, marked by the template, as they did before #1283. */
  @Test
  void keeps_a_hidden_order_and_carries_its_flag() {
    var order = order(customer, "VERSTECKT-01", null, true);
    suborder(order, "01", YESNO_YES);

    assertThat(customerorderRepository.findAllInvoiceable())
        .extracting(CustomerorderOption::hide)
        .containsExactly(true);
  }

  @Test
  void orders_the_choice_by_sign() {
    var other = customer("AND", "Anderer Kunde");
    suborder(order(customer, "ZETA", null, false), "01", YESNO_YES);
    suborder(order(other, "ALPHA", null, false), "01", YESNO_YES);
    suborder(order(customer, "MITTE", null, false), "01", YESNO_YES);

    assertThat(signs()).containsExactly("ALPHA", "MITTE", "ZETA");
  }

  private List<String> signs() {
    return customerorderRepository.findAllInvoiceable().stream().map(CustomerorderOption::sign).toList();
  }

  private Customer customer(String shortname, String name) {
    var created = new Customer();
    created.setShortname(shortname);
    created.setName(name);
    created.setAddress("Teststraße 1");
    return entityManager.persist(created);
  }

  private Customerorder order(Customer orderCustomer, String sign, String shortdescription, Boolean hide) {
    var order = new Customerorder();
    order.setCustomer(orderCustomer);
    order.setSign(sign);
    order.setShortdescription(shortdescription);
    order.setDescription("Vorgang");
    order.setFromDate(LocalDate.of(2026, 1, 1));
    order.setOrderType(OrderType.STANDARD);
    order.setDebithours(Duration.ZERO);
    order.setHide(hide);
    return entityManager.persist(order);
  }

  private void suborder(Customerorder order, String sign, char invoice) {
    var suborder = new Suborder();
    suborder.setCustomerorder(order);
    suborder.setSign(sign);
    suborder.setDescription("Beschreibung");
    suborder.setFromDate(LocalDate.of(2026, 1, 1));
    suborder.setDebithours(Duration.ZERO);
    suborder.setHide(false);
    suborder.setInvoice(invoice);
    entityManager.persist(suborder);
  }

}
