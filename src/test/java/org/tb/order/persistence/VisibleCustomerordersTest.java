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
import org.tb.common.LocalDateRange;
import org.tb.common.test.FixedClock;
import org.tb.customer.domain.Customer;
import org.tb.customer.persistence.CustomerRepository;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.OrderType;

/**
 * What a select box may offer (#1094). {@code hide} and the time criterion are two independent
 * decisions and are joined with <em>and</em>: an order is offered only when it is neither hidden nor
 * inactive. Joined with {@code or}, as this was before, neither of the two restricts anything —
 * everything but the intersection "hidden <em>and</em> expired" came back.
 *
 * <p>The time criterion is the one from ADR-0029 and looks at the end alone, so an order beginning
 * in the future stays offered: whoever enters it ahead of time has to find it again.
 */
@DataJpaTest
@Import({AuthorizedUserAuditorAware.class, CustomerorderDAO.class, SuborderDAO.class})
@FixedClock("2026-06-25T10:15:30")
@DisplayNameGeneration(ReplaceUnderscores.class)
class VisibleCustomerordersTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 25);
  private static final LocalDate LONG_AGO = TODAY.minusYears(1);

  @Autowired
  private CustomerorderDAO customerorderDAO;

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

  // --- die vier Kombinationen aus hide und Gueltigkeit -------------------------------------------

  @Test
  void offers_an_order_that_is_neither_hidden_nor_expired() {
    order("visible-and-running", false, LONG_AGO, TODAY.plusYears(1));

    assertThat(visibleSigns()).containsExactly("visible-and-running");
  }

  /** Die hide-Entscheidung greift, solange der Auftrag laeuft — vorher tat sie das nicht. */
  @Test
  void leaves_out_a_hidden_order_that_is_still_running() {
    order("hidden-but-running", true, LONG_AGO, TODAY.plusYears(1));

    assertThat(visibleSigns()).isEmpty();
  }

  @Test
  void leaves_out_an_expired_order_that_is_not_hidden() {
    order("expired-but-visible", false, LONG_AGO, TODAY.minusDays(1));

    assertThat(visibleSigns()).isEmpty();
  }

  @Test
  void leaves_out_an_order_that_is_hidden_and_expired() {
    order("hidden-and-expired", true, LONG_AGO, TODAY.minusDays(1));

    assertThat(visibleSigns()).isEmpty();
  }

  @Test
  void offers_exactly_one_of_the_four_combinations() {
    order("visible-and-running", false, LONG_AGO, TODAY.plusYears(1));
    order("hidden-but-running", true, LONG_AGO, TODAY.plusYears(1));
    order("expired-but-visible", false, LONG_AGO, TODAY.minusDays(1));
    order("hidden-and-expired", true, LONG_AGO, TODAY.minusDays(1));

    assertThat(visibleSigns()).containsExactly("visible-and-running");
  }

  // --- die Raender des Zeitkriteriums (ADR-0029) ------------------------------------------------

  /** Der Vergleich ist einschliessend: am letzten Tag ist der Auftrag noch zu waehlen. */
  @Test
  void offers_an_order_ending_today() {
    order("ends-today", false, LONG_AGO, TODAY);

    assertThat(visibleSigns()).containsExactly("ends-today");
  }

  @Test
  void offers_an_order_without_an_end_date() {
    order("open-end", false, LONG_AGO, null);

    assertThat(visibleSigns()).containsExactly("open-end");
  }

  @Test
  void offers_an_order_whose_open_end_is_stored_as_the_sentinel() {
    order("sentinel-end", false, LONG_AGO, LocalDateRange.FINIT_UNTIL_BOUNDARY);

    assertThat(visibleSigns()).containsExactly("sentinel-end");
  }

  /**
   * Nur das Ende zaehlt: ein im Voraus angelegter Auftrag bleibt waehlbar, sonst wird er ein zweites
   * Mal angelegt.
   */
  @Test
  void offers_an_order_that_begins_in_the_future() {
    order("starts-next-month", false, TODAY.plusMonths(1), TODAY.plusYears(1));

    assertThat(visibleSigns()).containsExactly("starts-next-month");
  }

  /**
   * Nach Kurzzeichen sortiert, ohne Beachtung der Gross-/Kleinschreibung — bei Beachtung stuenden
   * die drei in der Reihenfolge Alpha, Gamma, beta.
   */
  @Test
  void offers_the_orders_by_sign_ignoring_case() {
    order("beta", false, LONG_AGO, null);
    order("Alpha", false, LONG_AGO, null);
    order("Gamma", false, LONG_AGO, null);

    assertThat(visibleSigns()).containsExactly("Alpha", "beta", "Gamma");
  }

  /**
   * {@code hide IS NULL} heisst nicht verborgen — so liest es {@code Customerorder.getHide()}, und
   * so muss es auch die Abfrage lesen. Ein blosses {@code hide <> 1} waere in SQL fuer {@code NULL}
   * unbekannt und liesse die Zeile aus jeder Auswahlliste fallen (#1104).
   */
  @Test
  void offers_an_order_whose_hide_flag_was_never_set() {
    orderWithoutHideFlag("hide-never-set");

    assertThat(visibleSigns()).containsExactly("hide-never-set");
  }

  private List<String> visibleSigns() {
    return customerorderDAO.getVisibleCustomerorders().stream()
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

  private void order(String sign, boolean hidden, LocalDate fromDate, LocalDate untilDate) {
    var order = new Customerorder();
    order.setCustomer(customer);
    order.setSign(sign);
    order.setDescription(sign);
    order.setFromDate(fromDate);
    order.setUntilDate(untilDate);
    order.setOrderType(OrderType.STANDARD);
    order.setDebithours(Duration.ZERO);
    order.setHide(hidden);
    customerorderRepository.save(order);
  }

  /** Wie {@link #order}, laesst {@code hide} aber ungesetzt — die Spalte ist nullable. */
  private void orderWithoutHideFlag(String sign) {
    var order = new Customerorder();
    order.setCustomer(customer);
    order.setSign(sign);
    order.setDescription(sign);
    order.setFromDate(LONG_AGO);
    order.setOrderType(OrderType.STANDARD);
    order.setDebithours(Duration.ZERO);
    customerorderRepository.save(order);
  }
}
