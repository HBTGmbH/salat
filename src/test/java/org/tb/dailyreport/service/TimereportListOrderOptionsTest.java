package org.tb.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tb.customer.domain.Customer;
import org.tb.customer.service.CustomerService;
import org.tb.dailyreport.auth.TimereportVisibility;
import org.tb.dailyreport.auth.TimereportVisibility.Clause;
import org.tb.dailyreport.auth.TimereportVisibilityService;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.TimereportListDAO;
import org.tb.dailyreport.domain.TimereportFilterOptions.OrderOption;
import org.tb.dailyreport.persistence.TimereportListDAO.FilterValues;
import org.tb.dailyreport.service.TimereportListService.OrderGroup;
import org.tb.employee.service.EmployeeService;
import org.tb.jira.service.JiraTicketService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.OrderType;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

/**
 * Die Auftragsauswahl der Buchungsliste, beidseitig (#1106). Sie wird auf zwei Wegen gefuellt — die
 * Managerin bekommt die Stammdaten, alle anderen die Auftraege ihrer eigenen Buchungen —, und bis
 * #1106 filterten die beiden Wege verschieden: der eine ueber {@code hide} und die Gueltigkeit, der
 * andere nur ueber {@code hide}. Dieselbe Liste hatte damit zwei Bedeutungen, und welche man bekam,
 * hing an der Rolle statt an der Frage.
 *
 * <p>Entschieden ist die weite Lesart: die Liste schraenkt Vorhandenes ein, statt Neues auszuwaehlen
 * (der Controller kennt nur {@code GET}), und ein abgelaufener Auftrag behaelt seine Buchungen. Wer
 * sie nicht mehr anbietet, versteckt genau die Buchungen, die man ueber ihren Auftrag sucht. Fuer
 * die Auswahllisten der Stammdatenmasken hat #1094 das Gegenteil entschieden, und das bleibt so:
 * dort wird etwas Neues angelegt.
 *
 * <p>{@code hide} bleibt auf beiden Wegen ein eigenes Kriterium. Die Rolle entscheidet danach nur
 * noch ueber den Umfang, nicht mehr ueber das Filterkriterium.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportListOrderOptionsTest {

  private static final LocalDate LONG_AGO = LocalDate.now().minusYears(5);
  private static final int DIALOG_ROWS = 200;

  @Mock
  private TimereportListDAO timereportListDAO;
  @Mock
  private TimereportDAO timereportDAO;
  @Mock
  private TimereportVisibilityService visibilityService;
  @Mock
  private EmployeeService employeeService;
  @Mock
  private CustomerService customerService;
  @Mock
  private CustomerorderService customerorderService;
  @Mock
  private SuborderService suborderService;
  @Mock
  private JiraTicketService jiraTicketService;

  @InjectMocks
  private TimereportListService timereportListService;

  private final Customer customer = customer();
  private final Customerorder running = order(1L, "running", false, null);
  private final Customerorder inactive = order(2L, "inactive", false, LocalDate.now().minusDays(1));
  private final Customerorder hidden = order(3L, "hidden", true, null);

  // --- der unbeschraenkte Zweig: die Stammdaten ------------------------------------------------

  /**
   * Der Gegenbeweis steckt in der zweiten Verabredung: gaebe der Dienst weiterhin
   * {@code getVisibleCustomerorders()} aus — die Auswahlliste fuer Neues seit #1094 —, bekaeme die
   * Managerin nur den laufenden Auftrag und dieser Test fiele.
   */
  @Test
  void offers_an_inactive_order_to_an_unrestricted_user() {
    unrestricted();
    when(customerorderService.getNotHiddenCustomerorders()).thenReturn(List.of(running, inactive));
    lenient().when(customerorderService.getVisibleCustomerorders()).thenReturn(List.of(running));

    assertThat(offeredSigns()).containsExactly("running", "inactive");
  }

  // --- der beschraenkte Zweig: die Auftraege der eigenen Buchungen -----------------------------

  @Test
  void offers_an_inactive_order_to_a_restricted_user() {
    restrictedTo(running, inactive);

    assertThat(offeredSigns()).containsExactly("running", "inactive");
  }

  /**
   * {@code hide} ist die andere Frage und wird auf diesem Weg in Java beantwortet: was jemand von
   * Hand aus den Auswahllisten genommen hat, bleibt auch hier draussen — auch wenn eigene Buchungen
   * darauf stehen.
   */
  @Test
  void leaves_out_a_hidden_order_for_a_restricted_user() {
    restrictedTo(running, hidden);

    assertThat(offeredSigns()).containsExactly("running");
  }

  // --- und beide zusammen ----------------------------------------------------------------------

  /**
   * Der Kern von #1106: bei gleichem Auftragsbestand bieten beide Zweige dasselbe an. Die
   * Sichtbarkeit bestimmt, <em>welche</em> Auftraege in den Zweig geraten, nicht, wonach er filtert.
   */
  @Test
  void offers_the_same_orders_on_both_paths() {
    unrestricted();
    when(customerorderService.getNotHiddenCustomerorders()).thenReturn(List.of(running, inactive));
    var unrestrictedSigns = offeredSigns();

    restrictedTo(running, inactive, hidden);

    assertThat(offeredSigns()).isEqualTo(unrestrictedSigns);
  }

  private void unrestricted() {
    when(visibilityService.anyTime()).thenReturn(TimereportVisibility.all());
    when(suborderService.getNotHiddenSuborders()).thenReturn(List.of());
  }

  /** Eine Person, die genau die Auftraege sieht, auf die sie selbst gebucht hat. */
  private void restrictedTo(Customerorder... bookedOn) {
    var visibility = TimereportVisibility.of(List.of(Clause.forEmployees(Set.of(7L))));
    var orderIds = List.of(bookedOn).stream().map(Customerorder::getId).toList();
    when(visibilityService.anyTime()).thenReturn(visibility);
    when(timereportListDAO.findFilterValues(visibility))
        .thenReturn(new FilterValues(List.of(7L), List.of(customer.getId()), orderIds, List.of(), List.of()));
    when(customerorderService.getCustomerordersByIds(orderIds)).thenReturn(List.of(bookedOn));
    when(suborderService.getSubordersByIds(List.of())).thenReturn(List.of());
  }

  /** Nur die Auftraege des Dialogs; die Unterauftragsebene ist hier abgeschaltet. */
  private List<String> offeredSigns() {
    return timereportListService.searchOrders("", List.of(), true, false, DIALOG_ROWS)
        .groups().stream()
        .map(OrderGroup::order)
        .map(OrderOption::sign)
        .toList();
  }

  private Customer customer() {
    var created = new Customer();
    setField(created, "id", 42L);
    created.setName("Testkunde");
    created.setShortname("TK");
    return created;
  }

  private Customerorder order(long id, String sign, boolean hide, LocalDate untilDate) {
    var created = new Customerorder();
    setField(created, "id", id);
    created.setCustomer(customer);
    created.setSign(sign);
    created.setDescription(sign);
    created.setFromDate(LONG_AGO);
    created.setUntilDate(untilDate);
    created.setOrderType(OrderType.STANDARD);
    created.setDebithours(Duration.ZERO);
    created.setHide(hide);
    return created;
  }
}
