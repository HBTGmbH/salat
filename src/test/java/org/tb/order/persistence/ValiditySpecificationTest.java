package org.tb.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.StreamSupport;
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
import org.tb.common.LocalDateRange;
import org.tb.common.Validity;
import org.tb.common.test.FixedClock;
import org.tb.customer.domain.Customer;
import org.tb.customer.persistence.CustomerRepository;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Customerorder_;
import org.tb.order.domain.OrderType;

/**
 * The query side of the rule from #950, against a database: what
 * {@link Validity#notInactive(jakarta.persistence.metamodel.SingularAttribute)} selects has to be
 * exactly what {@link Validity#isInactive(LocalDate)} says in Java — otherwise a list and the row it
 * renders disagree about the same record.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@FixedClock("2026-06-25T10:15:30")
@DisplayNameGeneration(ReplaceUnderscores.class)
class ValiditySpecificationTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 25);

  @Autowired
  private CustomerorderRepository customerorderRepository;

  @Autowired
  private CustomerRepository customerRepository;

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
  void leaves_out_an_end_before_today() {
    order("ended-yesterday", TODAY.minusYears(1), TODAY.minusDays(1));

    assertThat(notInactive()).isEmpty();
  }

  @Test
  void keeps_an_end_on_today() {
    order("ends-today", TODAY.minusYears(1), TODAY);

    assertThat(notInactive()).containsExactly("ends-today");
  }

  @Test
  void keeps_an_open_end_stored_as_null() {
    order("open-end", TODAY.minusYears(1), null);

    assertThat(notInactive()).containsExactly("open-end");
  }

  @Test
  void keeps_an_open_end_stored_as_the_sentinel() {
    order("sentinel-end", TODAY.minusYears(1), LocalDateRange.FINIT_UNTIL_BOUNDARY);

    assertThat(notInactive()).containsExactly("sentinel-end");
  }

  /** A record entered ahead of time is not inactive, and the list must not swallow it. */
  @Test
  void keeps_a_start_in_the_future() {
    order("starts-next-month", TODAY.plusMonths(1), TODAY.plusYears(1));

    assertThat(notInactive()).containsExactly("starts-next-month");
  }

  @Test
  void selects_exactly_what_the_java_side_calls_active() {
    order("ended-yesterday", TODAY.minusYears(1), TODAY.minusDays(1));
    order("ends-today", TODAY.minusYears(1), TODAY);
    order("open-end", TODAY.minusYears(1), null);
    order("sentinel-end", TODAY.minusYears(1), LocalDateRange.FINIT_UNTIL_BOUNDARY);
    order("starts-next-month", TODAY.plusMonths(1), TODAY.plusYears(1));

    var inJava = StreamSupport.stream(customerorderRepository.findAll().spliterator(), false)
        .filter(Customerorder::getCurrentlyValid)
        .map(Customerorder::getSign)
        .sorted()
        .toList();

    assertThat(notInactive().stream().sorted().toList()).isEqualTo(inJava);
  }

  private List<String> notInactive() {
    return customerorderRepository
        .findAll(Validity.<Customerorder>notInactive(Customerorder_.untilDate)).stream()
        .map(Customerorder::getSign)
        .toList();
  }

  private Customer customer() {
    var created = new Customer();
    created.setName("Testkunde");
    created.setShortname("TK");
    created.setAddress("Teststraße 1");
    return customerRepository.save(created);
  }

  private void order(String sign, LocalDate fromDate, LocalDate untilDate) {
    var order = new Customerorder();
    order.setCustomer(customer);
    order.setSign(sign);
    order.setDescription(sign);
    order.setFromDate(fromDate);
    order.setUntilDate(untilDate);
    order.setOrderType(OrderType.STANDARD);
    order.setDebithours(Duration.ZERO);
    order.setHide(false);
    customerorderRepository.save(order);
  }
}
