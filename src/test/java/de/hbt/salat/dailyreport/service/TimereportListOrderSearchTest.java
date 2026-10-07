package de.hbt.salat.dailyreport.service;

import static de.hbt.salat.testutils.CustomerTestUtils.uniqueShortname;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.command.CommandPublisher;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.customer.persistence.CustomerDAO;
import de.hbt.salat.customer.service.CustomerService;
import de.hbt.salat.dailyreport.auth.TimereportAuthorization;
import de.hbt.salat.dailyreport.auth.TimereportVisibility;
import de.hbt.salat.dailyreport.auth.TimereportVisibility.Clause;
import de.hbt.salat.dailyreport.auth.TimereportVisibilityService;
import de.hbt.salat.dailyreport.domain.TimereportFilterOptions.SuborderOption;
import de.hbt.salat.dailyreport.persistence.TimereportDAO;
import de.hbt.salat.dailyreport.persistence.TimereportListDAO;
import de.hbt.salat.dailyreport.persistence.TimereportListDAO.FilterValues;
import de.hbt.salat.dailyreport.service.TimereportListService.OrderSearchResult;
import de.hbt.salat.employee.persistence.EmployeeDAO;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.jira.service.JiraTicketService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.persistence.CustomerorderDAO;
import de.hbt.salat.order.persistence.SuborderDAO;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;
import de.hbt.salat.order.service.SpecialOrders;

/**
 * Was der Auftragsdialog der Buchungsliste findet (#1331): Suche, Auftraggeberfilter, Ebenenfilter, eingeschraenkte
 * Sicht, Reihenfolge und Obergrenze — gegen die echten Dienste des Auftragsmoduls und eine echte Datenbank, damit der
 * Test haelt, gleich auf welchem Weg der Dialog seine Zeilen holt.
 *
 * <p>Der Baum der Testdaten:
 * <pre>
 * ALPHA  (Nord)  Wartung
 *   ALPHA/01          Analyse
 *     ALPHA/01/01     Konzept
 *       ALPHA/01/01/01 Feinkonzept
 *     ALPHA/01/02     Altlast        verborgen
 *   ALPHA/02          (ohne Kurzbeschreibung, lange Beschreibung)
 * beta   (Nord)  Betrieb
 *   beta/01           Support
 * GAMMA  (Nord)  verborgen
 *   GAMMA/01          Rest           selbst nicht verborgen
 * DELTA  (Sued)  Entwicklung
 *   DELTA/01          Test_Lauf
 *   DELTA/02          100% Abdeckung
 * </pre>
 */
@DataJpaTest
@DisplayNameGeneration(ReplaceUnderscores.class)
@Import({AuthorizedUserAuditorAware.class, SalatProperties.class, TimereportListService.class, SuborderService.class,
    SuborderDAO.class, CustomerorderService.class, CustomerorderDAO.class, CustomerDAO.class, CommandPublisher.class,
    SpecialOrders.class})
class TimereportListOrderSearchTest {

  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final int DIALOG_ROWS = 200;

  @Autowired
  private TimereportListService timereportListService;

  @Autowired
  private TestEntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;
  @MockitoBean
  private EmployeeDAO employeeDAO;
  @MockitoBean
  private TimereportListDAO timereportListDAO;
  @MockitoBean
  private TimereportDAO timereportDAO;
  @MockitoBean
  private TimereportVisibilityService visibilityService;
  @MockitoBean
  private TimereportAuthorization timereportAuthorization;
  @MockitoBean
  private EmployeeService employeeService;
  @MockitoBean
  private CustomerService customerService;
  @MockitoBean
  private JiraTicketService jiraTicketService;

  private Customer nord;
  private Customer sued;
  private Customerorder alpha;
  private Customerorder gamma;
  private Customerorder delta;
  private Suborder alpha01;
  private Suborder alpha0101;
  private Suborder alpha0102;
  private Suborder delta01;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");

    // Andere Testklassen schreiben in dieselbe Datenbank und lassen ihre Auftraege dort stehen. Verborgen bietet der
    // Dialog sie nicht an; die Transaktion des Tests nimmt das am Ende wieder zurueck.
    entityManager.getEntityManager().createQuery("update Customerorder c set c.hide = true").executeUpdate();
    entityManager.getEntityManager().createQuery("update Suborder s set s.hide = true").executeUpdate();

    nord = customer("Nord");
    sued = customer("Sued");
    alpha = order(nord, "ALPHA", "Wartung", false);
    var beta = order(nord, "beta", "Betrieb", false);
    gamma = order(nord, "GAMMA", "Altbestand", true);
    delta = order(sued, "DELTA", "Entwicklung", false);

    alpha01 = suborder(alpha, null, "01", "Analyse", false);
    alpha0101 = suborder(alpha, alpha01, "01", "Konzept", false);
    suborder(alpha, alpha0101, "01", "Feinkonzept", false);
    alpha0102 = suborder(alpha, alpha01, "02", "Altlast", true);
    var alpha02 = suborder(alpha, null, "02", "", false);
    alpha02.setDescription("Wartung und Betrieb der Altanlagen");
    suborder(beta, null, "01", "Support", false);
    suborder(gamma, null, "01", "Rest", false);
    delta01 = suborder(delta, null, "01", "Test_Lauf", false);
    suborder(delta, null, "02", "100% Abdeckung", false);

    // frisch aus der Datenbank, wie in einem Request — sonst kennt kein Elternteil seine Kinder
    entityManager.flush();
    entityManager.clear();
  }

  // --- ohne Suchbegriff ------------------------------------------------------------------------

  /**
   * Auftraege nach Kuerzel ohne Gross-/Kleinschreibung, darunter ihre Unterauftraege nach vollstaendigem Kuerzel.
   * Verborgenes faellt weg, ein nicht verborgener Unterauftrag unter einem verborgenen Auftrag steht ohne seinen
   * Auftrag am Ende.
   */
  @Test
  void without_a_term_offers_everything_not_hidden_as_a_tree() {
    unrestricted();

    var result = search("", List.of(), true, true, DIALOG_ROWS);

    assertThat(rows(result)).containsExactly(
        "ALPHA",
        "  ALPHA/01",
        "  ALPHA/01/01",
        "  ALPHA/01/01/01",
        "  ALPHA/02",
        "beta",
        "  beta/01",
        "DELTA",
        "  DELTA/01",
        "  DELTA/02",
        "~ GAMMA/01");
    assertThat(result.total()).isEqualTo(11);
    assertThat(result.shown()).isEqualTo(11);
  }

  /** Ebene, Elternteil und wie viele Unterauftraege eine Auswahl mitnimmt — verborgene eingeschlossen. */
  @Test
  void a_suborder_knows_its_place_in_the_tree() {
    unrestricted();

    var result = search("", List.of(), true, true, DIALOG_ROWS);

    var alphaRows = result.groups().getFirst().suborders();
    assertThat(alphaRows).extracting(SuborderOption::completeSign, SuborderOption::level,
            SuborderOption::descendantCount, SuborderOption::customerOrderId)
        .containsExactly(
            tuple("ALPHA/01", 0, 3, alpha.getId()),
            tuple("ALPHA/01/01", 1, 1, alpha.getId()),
            tuple("ALPHA/01/01/01", 2, 0, alpha.getId()),
            tuple("ALPHA/02", 0, 0, alpha.getId()));
    assertThat(alphaRows).extracting(SuborderOption::parentId)
        .containsExactly(null, alpha01.getId(), alpha0101.getId(), null);
    assertThat(alphaRows).extracting(SuborderOption::description)
        .containsExactly("Analyse", "Konzept", "Feinkonzept", "Wartung und Betri...");
    assertThat(result.groups().getFirst().order().description()).isEqualTo("Wartung");
    assertThat(result.groups().getFirst().order().customerId()).isEqualTo(nord.getId());
    assertThat(result.groups().getFirst().order().customerShortname()).isEqualTo(nord.getShortname());
  }

  // --- Suche -----------------------------------------------------------------------------------

  @Test
  void the_term_finds_an_order_by_its_sign_and_a_suborder_by_its_complete_sign_regardless_of_case() {
    unrestricted();

    assertThat(rows(search("alp", List.of(), true, true, DIALOG_ROWS))).containsExactly(
        "ALPHA", "  ALPHA/01", "  ALPHA/01/01", "  ALPHA/01/01/01", "  ALPHA/02");
    assertThat(rows(search("  01/01 ", List.of(), true, true, DIALOG_ROWS))).containsExactly(
        "~ ALPHA/01/01", "~ ALPHA/01/01/01");
  }

  /**
   * Die Kurzbeschreibung so, wie der Dialog sie zeigt: ohne eigene die gekuerzte Beschreibung. Was hinter der Kuerzung
   * steht, findet die Suche nicht.
   */
  @Test
  void the_term_finds_by_the_short_description_as_shown() {
    unrestricted();

    assertThat(rows(search("BETRI", List.of(), true, true, DIALOG_ROWS))).containsExactly(
        "beta", "~ ALPHA/02");
    assertThat(rows(search("konzept", List.of(), true, true, DIALOG_ROWS))).containsExactly(
        "~ ALPHA/01/01", "~ ALPHA/01/01/01");
    assertThat(search("anlagen", List.of(), true, true, DIALOG_ROWS).total()).isZero();
  }

  /** Den Auftraggeber kennt die Zeile des Auftrags, nicht die des Unterauftrags. */
  @Test
  void the_term_finds_an_order_by_its_customer_but_no_suborder() {
    unrestricted();

    assertThat(rows(search(sued.getShortname().toLowerCase(), List.of(), true, true, DIALOG_ROWS)))
        .containsExactly("DELTA");
  }

  @Test
  void percent_and_underscore_in_the_term_are_taken_literally() {
    unrestricted();

    assertThat(rows(search("t_l", List.of(), true, true, DIALOG_ROWS))).containsExactly("~ DELTA/01");
    assertThat(rows(search("0% a", List.of(), true, true, DIALOG_ROWS))).containsExactly("~ DELTA/02");
    assertThat(search("%", List.of(), true, true, DIALOG_ROWS).total()).isEqualTo(1);
    assertThat(search("_", List.of(), true, true, DIALOG_ROWS).total()).isEqualTo(1);
  }

  @Test
  void a_term_nothing_contains_finds_nothing() {
    unrestricted();

    var result = search("xyz", List.of(), true, true, DIALOG_ROWS);

    assertThat(rows(result)).isEmpty();
    assertThat(result.total()).isZero();
  }

  // --- Auftraggeberfilter ----------------------------------------------------------------------

  /**
   * Nur die Auftraege der gewaehlten Auftraggeber und die Unterauftraege darunter. Unter einem verborgenen Auftrag
   * bleibt hier auch der nicht verborgene Unterauftrag draussen.
   */
  @Test
  void a_chosen_customer_narrows_to_its_orders_and_their_suborders() {
    unrestricted();

    assertThat(rows(search("", List.of(nord.getId()), true, true, DIALOG_ROWS))).containsExactly(
        "ALPHA", "  ALPHA/01", "  ALPHA/01/01", "  ALPHA/01/01/01", "  ALPHA/02", "beta", "  beta/01");
    assertThat(rows(search("/01", List.of(sued.getId()), true, true, DIALOG_ROWS))).containsExactly(
        "~ DELTA/01");
    assertThat(rows(search("", List.of(nord.getId(), sued.getId()), false, true, DIALOG_ROWS))).containsExactly(
        "~ ALPHA/01", "~ ALPHA/01/01", "~ ALPHA/01/01/01", "~ ALPHA/02", "~ DELTA/01", "~ DELTA/02",
        "~ beta/01");
  }

  // --- Ebenenfilter ----------------------------------------------------------------------------

  @Test
  void the_order_level_alone_shows_no_suborder() {
    unrestricted();

    var result = search("", List.of(), true, false, DIALOG_ROWS);

    assertThat(rows(result)).containsExactly("ALPHA", "beta", "DELTA");
    assertThat(result.total()).isEqualTo(3);
  }

  /** Ohne die Auftraege stehen alle Unterauftraege flach, nach ihrem vollstaendigen Kuerzel. */
  @Test
  void the_suborder_level_alone_shows_every_suborder_flat() {
    unrestricted();

    var result = search("", List.of(), false, true, DIALOG_ROWS);

    assertThat(rows(result)).containsExactly(
        "~ ALPHA/01", "~ ALPHA/01/01", "~ ALPHA/01/01/01", "~ ALPHA/02", "~ DELTA/01", "~ DELTA/02",
        "~ GAMMA/01", "~ beta/01");
    assertThat(result.total()).isEqualTo(8);
  }

  // --- Obergrenze ------------------------------------------------------------------------------

  /** Auftraege zuerst, die Unterauftraege fuellen den Rest; gezaehlt wird alles, was die Suche findet. */
  @Test
  void the_limit_takes_the_orders_first_and_fills_up_with_suborders() {
    unrestricted();

    var result = search("", List.of(), true, true, 4);

    assertThat(rows(result)).containsExactly("ALPHA", "  ALPHA/01", "beta", "DELTA");
    assertThat(result.total()).isEqualTo(11);
    assertThat(result.shown()).isEqualTo(4);
  }

  @Test
  void a_limit_the_orders_fill_leaves_no_suborder() {
    unrestricted();

    var result = search("", List.of(), true, true, 2);

    assertThat(rows(result)).containsExactly("ALPHA", "beta");
    assertThat(result.total()).isEqualTo(11);
  }

  /** Die Unterauftraege werden nach ihrem vollstaendigen Kuerzel abgeschnitten, Grossbuchstaben vor kleinen. */
  @Test
  void the_limit_cuts_the_suborders_in_the_order_of_their_complete_sign() {
    unrestricted();

    var result = search("", List.of(), false, true, 7);

    assertThat(rows(result)).containsExactly(
        "~ ALPHA/01", "~ ALPHA/01/01", "~ ALPHA/01/01/01", "~ ALPHA/02", "~ DELTA/01", "~ DELTA/02",
        "~ GAMMA/01");
    assertThat(result.total()).isEqualTo(8);
  }

  // --- eingeschraenkte Sicht -------------------------------------------------------------------

  /**
   * Wer nicht alles sieht, bekommt die Auftraege und Unterauftraege seiner sichtbaren Buchungen — ohne die verborgenen,
   * und ein Unterauftrag zeigt seinen Platz im Baum auch dann, wenn sein Elternteil nicht dabei ist.
   */
  @Test
  void a_restricted_user_gets_what_occurs_in_the_bookings_they_may_read() {
    restrictedTo(List.of(alpha, gamma, delta), List.of(alpha0101, alpha0102, delta01));

    var result = search("", List.of(), true, true, DIALOG_ROWS);

    assertThat(rows(result)).containsExactly("ALPHA", "  ALPHA/01/01", "DELTA", "  DELTA/01");
    assertThat(result.total()).isEqualTo(4);
    var konzept = result.groups().getFirst().suborders().getFirst();
    assertThat(konzept.level()).isEqualTo(1);
    assertThat(konzept.parentId()).isEqualTo(alpha01.getId());
    assertThat(konzept.descendantCount()).isEqualTo(1);
  }

  @Test
  void a_restricted_user_searches_and_filters_within_what_they_may_read() {
    restrictedTo(List.of(alpha, gamma, delta), List.of(alpha0101, alpha0102, delta01));

    assertThat(rows(search("/01", List.of(), true, true, DIALOG_ROWS))).containsExactly(
        "~ ALPHA/01/01", "~ DELTA/01");
    assertThat(rows(search("", List.of(sued.getId()), true, true, DIALOG_ROWS))).containsExactly(
        "DELTA", "  DELTA/01");
    assertThat(rows(search("", List.of(), false, true, DIALOG_ROWS))).containsExactly(
        "~ ALPHA/01/01", "~ DELTA/01");
  }

  @Test
  void whoever_may_read_nothing_gets_nothing() {
    when(visibilityService.anyTime()).thenReturn(TimereportVisibility.of(List.of()));

    var result = search("", List.of(), true, true, DIALOG_ROWS);

    assertThat(rows(result)).isEmpty();
    assertThat(result.total()).isZero();
  }

  // --- abgelaufene Auftraege ------------------------------------------------------------------

  /**
   * Ein abgelaufener Auftrag steht im Dialog (#1106): er behaelt seine Buchungen, und wer nach ihnen sucht, sucht ueber
   * ihn. Beide Wege filtern allein nach {@code hide}; die Sicht entscheidet nur, wie viel jemand sieht.
   */
  @Test
  void an_ended_order_is_offered_on_both_paths() {
    var ended = order(sued, "OMEGA", "Abgeschlossen", false);
    ended.setUntilDate(FROM.plusDays(1));
    var endedSuborder = suborder(ended, null, "01", "Abschluss", false);
    endedSuborder.setUntilDate(FROM.plusDays(1));
    entityManager.flush();
    entityManager.clear();

    unrestricted();
    assertThat(rows(search("omega", List.of(), true, true, DIALOG_ROWS))).containsExactly("OMEGA", "  OMEGA/01");

    restrictedTo(List.of(ended), List.of(endedSuborder));
    assertThat(rows(search("omega", List.of(), true, true, DIALOG_ROWS))).containsExactly("OMEGA", "  OMEGA/01");
  }

  // --- Hilfen ----------------------------------------------------------------------------------

  private OrderSearchResult search(String term, List<Long> customerIds, boolean includeOrders,
      boolean includeSuborders, int limit) {
    return timereportListService.searchOrders(term, customerIds, includeOrders, includeSuborders, limit);
  }

  /** Der Dialog als Text: ein Auftrag, eingerueckt seine Unterauftraege, mit {@code ~} die ohne ihren Auftrag. */
  private static List<String> rows(OrderSearchResult result) {
    var rows = new ArrayList<String>();
    result.groups().forEach(group -> {
      rows.add(group.order().sign());
      group.suborders().forEach(suborder -> rows.add("  " + suborder.completeSign()));
    });
    result.orphans().forEach(suborder -> rows.add("~ " + suborder.completeSign()));
    return rows;
  }

  private void unrestricted() {
    when(visibilityService.anyTime()).thenReturn(TimereportVisibility.all());
  }

  private void restrictedTo(List<Customerorder> orders, List<Suborder> suborders) {
    var visibility = TimereportVisibility.of(List.of(Clause.forEmployees(Set.of(7L))));
    when(visibilityService.anyTime()).thenReturn(visibility);
    when(timereportListDAO.findFilterValues(visibility)).thenReturn(new FilterValues(List.of(7L),
        orders.stream().map(order -> order.getCustomer().getId()).distinct().toList(),
        orders.stream().map(Customerorder::getId).toList(),
        suborders.stream().map(Suborder::getId).toList()));
  }

  private Customer customer(String prefix) {
    var created = new Customer();
    created.setShortname(uniqueShortname(prefix));
    created.setName("Kunde " + prefix);
    created.setAddress("Teststraße 1");
    return entityManager.persist(created);
  }

  private Customerorder order(Customer customer, String sign, String shortdescription, boolean hide) {
    var created = new Customerorder();
    created.setCustomer(customer);
    created.setSign(sign);
    created.setDescription("Auftrag " + sign);
    created.setShortdescription(shortdescription);
    created.setFromDate(FROM);
    created.setOrderType(OrderType.STANDARD);
    created.setDebithours(Duration.ZERO);
    created.setHide(hide);
    return entityManager.persist(created);
  }

  private Suborder suborder(Customerorder customerorder, Suborder parent, String sign, String shortdescription,
      boolean hide) {
    var created = new Suborder();
    created.setCustomerorder(customerorder);
    created.setParentorder(parent);
    created.setSign(sign);
    created.setDescription("Leistung " + sign);
    created.setShortdescription(shortdescription);
    created.setFromDate(FROM);
    created.setDebithours(Duration.ZERO);
    created.setHide(hide);
    if (parent != null) {
      parent.addSuborder(created);
    }
    return entityManager.persist(created);
  }
}
