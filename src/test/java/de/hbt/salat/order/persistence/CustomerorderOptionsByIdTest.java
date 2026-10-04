package de.hbt.salat.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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

/**
 * What another module reads to name the orders it refers to by id (#1212): the orders as a select
 * offers them, hidden ones included, ordered by sign — and the id behind a sign a user picked or a
 * module was handed.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class CustomerorderOptionsByIdTest {

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
    customer = customer();
  }

  @Test
  void names_the_orders_with_the_given_ids_ordered_by_sign() {
    var zeta = order("ZETA", false);
    var alpha = order("ALPHA", false);
    order("OTHER", false);

    assertThat(customerorderRepository.findOptionsByIdIn(List.of(zeta.getId(), alpha.getId())))
        .containsExactly(
            new CustomerorderOption(alpha.getId(), "ALPHA", "Kurz", "Vorgang", "BSP", "Beispielkunde", false),
            new CustomerorderOption(zeta.getId(), "ZETA", "Kurz", "Vorgang", "BSP", "Beispielkunde", false));
  }

  /** A record outlives its order's visibility, and the list it sits in still has to name the order. */
  @Test
  void names_a_hidden_order_and_carries_its_flag() {
    var hidden = order("VERSTECKT", true);

    assertThat(customerorderRepository.findOptionsByIdIn(List.of(hidden.getId())))
        .extracting(CustomerorderOption::hide).containsExactly(true);
  }

  @Test
  void finds_the_id_behind_a_sign() {
    var order = order("MUSTER-01", false);

    assertThat(customerorderRepository.findIdBySign("MUSTER-01")).contains(order.getId());
    assertThat(customerorderRepository.findIdBySign("UNBEKANNT")).isEmpty();
  }

  /** The palette hands its orders over by sign, the budget links them by id (#1334). */
  @Test
  void finds_the_orders_behind_several_signs_and_skips_an_unknown_one() {
    var zeta = order("ZETA", false);
    var alpha = order("ALPHA", true);
    order("OTHER", false);

    assertThat(customerorderRepository.findOptionsBySignIn(List.of("ZETA", "ALPHA", "UNBEKANNT")))
        .extracting(CustomerorderOption::id, CustomerorderOption::sign)
        .containsExactly(tuple(alpha.getId(), "ALPHA"), tuple(zeta.getId(), "ZETA"));
  }

  private Customer customer() {
    var created = new Customer();
    created.setShortname("BSP");
    created.setName("Beispielkunde");
    created.setAddress("Teststraße 1");
    return entityManager.persist(created);
  }

  private Customerorder order(String sign, Boolean hide) {
    var order = new Customerorder();
    order.setCustomer(customer);
    order.setSign(sign);
    order.setShortdescription("Kurz");
    order.setDescription("Vorgang");
    order.setFromDate(LocalDate.of(2026, 1, 1));
    order.setOrderType(OrderType.STANDARD);
    order.setDebithours(Duration.ZERO);
    order.setHide(hide);
    return entityManager.persist(order);
  }

}
