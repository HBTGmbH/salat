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
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.persistence.AuthorizedUserAuditorAware;
import org.tb.common.GlobalConstants;
import org.tb.common.palette.PaletteQuery;
import org.tb.common.test.FixedClock;
import org.tb.customer.domain.Customer;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.CustomerorderSearchRow;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.OrderType;
import org.tb.order.domain.Suborder;
import org.tb.order.domain.SuborderSearchRow;
import org.tb.order.domain.SuborderSignRow;

/**
 * The candidates the command palette asks the database for (#1157): orders and suborders that
 * contain every typed word in one of their fields, case-insensitively, with {@code %} and {@code _}
 * taken literally. Hidden and ended objects are candidates as well — the palette shows them ranked
 * lower and marked (ADR-0012, ADR-0029) —, but they come last, so that the limit cuts them first.
 * For a restricted user the suborders narrow to what that person may book today.
 *
 * <p>The words are made by {@link PaletteQuery}, as the services make them, so that the escaping of
 * the words and the {@code escape} clause of the queries are tested together.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@FixedClock("2026-06-25T10:15:30")
@DisplayNameGeneration(ReplaceUnderscores.class)
class PaletteCandidatesTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 25);
  private static final LocalDate LONG_AGO = TODAY.minusYears(1);

  @Autowired
  private CustomerorderRepository customerorderRepository;

  @Autowired
  private SuborderRepository suborderRepository;

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

  // --- orders: which fields are searched ------------------------------------------------------

  @Test
  void finds_an_order_by_a_part_of_its_sign() {
    order("MUSTER-01");
    order("ANDERE-02");

    assertThat(orderSigns("ster-0")).containsExactly("MUSTER-01");
  }

  @Test
  void finds_an_order_by_its_short_description() {
    order(customer, "A-01", "Wartungsvertrag", "Vorgang");
    order(customer, "A-02", "Weiterentwicklung", "Vorgang");

    assertThat(orderSigns("wartung")).containsExactly("A-01");
  }

  @Test
  void finds_an_order_by_its_description() {
    order(customer, "A-01", null, "Betrieb der Plattform");
    order("A-02");

    assertThat(orderSigns("plattform")).containsExactly("A-01");
  }

  @Test
  void finds_an_order_by_the_short_name_of_its_customer() {
    order(customer("MK", "Musterkunde"), "A-01", null, "Vorgang");
    order("A-02");

    assertThat(orderSigns("mk")).containsExactly("A-01");
  }

  @Test
  void finds_an_order_by_the_name_of_its_customer() {
    order(customer("MK", "Musterkunde"), "A-01", null, "Vorgang");
    order("A-02");

    assertThat(orderSigns("musterk")).containsExactly("A-01");
  }

  /** {@code lower} on the column: the collation is not case-insensitive everywhere. */
  @Test
  void ignores_case_in_the_query_and_in_the_data() {
    order("MuStEr-01");

    assertThat(orderSigns("mUsTeR-01")).containsExactly("MuStEr-01");
  }

  /** Every word has to stand somewhere, but not all of them in the same field. */
  @Test
  void requires_every_word_but_in_any_of_the_fields() {
    var musterkunde = customer("MK", "Musterkunde");
    order(musterkunde, "MUSTER-01", "Wartung", "Vorgang");
    order(musterkunde, "MUSTER-02", "Weiterentwicklung", "Vorgang");
    order(customer, "ANDERE-03", "Wartung", "Vorgang");

    assertThat(orderSigns("musterkunde wartung")).containsExactly("MUSTER-01");
  }

  @Test
  void narrows_by_a_third_word() {
    var musterkunde = customer("MK", "Musterkunde");
    order(musterkunde, "MUSTER-01", "Wartung", "Vorgang");
    order(musterkunde, "MUSTER-02", "Wartung", "Vorgang");

    assertThat(orderSigns("mk wartung 02")).containsExactly("MUSTER-02");
  }

  // --- orders: wildcards typed into the palette -------------------------------------------------

  /** An underscore is a character like any other, not the wildcard for one. */
  @Test
  void takes_an_underscore_literally() {
    order("PLAN_A");
    order("PLANXA");

    assertThat(orderSigns("plan_a")).containsExactly("PLAN_A");
  }

  @Test
  void takes_a_percent_sign_literally() {
    order(customer, "R-01", "Rabatt 50%", "Vorgang");
    order(customer, "R-02", "Rabatt 500", "Vorgang");

    assertThat(orderSigns("50%")).containsExactly("R-01");
  }

  /** The escape character itself is escaped, or a word ending in it would be no valid pattern. */
  @Test
  void takes_the_escape_character_literally() {
    order("WOW!-01");
    order("WOW-01");

    assertThat(orderSigns("wow!")).containsExactly("WOW!-01");
  }

  // --- orders: hidden and ended ones last ------------------------------------------------------

  @Test
  void puts_ended_orders_after_the_current_ones() {
    order("A-ENDED", false, TODAY.minusDays(1));
    order("Z-RUNNING", false, TODAY.plusYears(1));

    assertThat(orderSigns("vorgang")).containsExactly("Z-RUNNING", "A-ENDED");
  }

  /** Hidden weighs more than ended: an ended order that is not hidden comes before a hidden one. */
  @Test
  void puts_hidden_orders_after_the_ended_ones() {
    order("A-HIDDEN", true, TODAY.plusYears(1));
    order("B-HIDDEN-AND-ENDED", true, TODAY.minusDays(1));
    order("Y-ENDED", false, TODAY.minusDays(1));
    order("Z-RUNNING", false, null);

    assertThat(orderSigns("vorgang"))
        .containsExactly("Z-RUNNING", "Y-ENDED", "A-HIDDEN", "B-HIDDEN-AND-ENDED");
  }

  /**
   * An end today and an open end are current (ADR-0029), a {@code hide} never set is not hidden
   * (#1104) — none of them may sink to the end of the list.
   */
  @Test
  void ranks_an_end_today_an_open_end_and_an_unset_hide_flag_as_current() {
    order("A-ENDED", false, TODAY.minusDays(1));
    order("X-ENDS-TODAY", false, TODAY);
    order("Y-OPEN-END", false, null);
    order("Z-HIDE-NEVER-SET", null, null);

    assertThat(orderSigns("vorgang"))
        .containsExactly("X-ENDS-TODAY", "Y-OPEN-END", "Z-HIDE-NEVER-SET", "A-ENDED");
  }

  @Test
  void cuts_hidden_and_ended_orders_first_at_the_limit() {
    order("A-HIDDEN", true, null);
    order("B-ENDED", false, TODAY.minusDays(1));
    order("C-RUNNING", false, null);
    order("D-RUNNING", false, null);

    assertThat(orderRows("vorgang", 2))
        .extracting(CustomerorderSearchRow::sign)
        .containsExactly("C-RUNNING", "D-RUNNING");
  }

  @Test
  void reads_the_order_and_its_customer_into_one_row() {
    var musterkunde = customer("MK", "Musterkunde");
    var order = order(musterkunde, "MUSTER-01", "Wartungsvertrag", "Wartung der Anlage", true,
        TODAY.minusDays(1));

    assertThat(orderRows("muster-01", PaletteQuery.CANDIDATE_LIMIT)).containsExactly(
        new CustomerorderSearchRow(order.getId(), "MUSTER-01", "Wartungsvertrag", "Wartung der Anlage",
            musterkunde.getId(), "MK", "Musterkunde", true, TODAY.minusDays(1)));
  }

  // --- suborders: which fields are searched ---------------------------------------------------

  @Test
  void finds_a_suborder_by_its_own_sign() {
    var order = order("MUSTER-01");
    var wanted = suborder(order, "S-17");
    suborder(order, "S-23");

    assertThat(suborderIds("s-17", null)).containsExactly(wanted.getId());
  }

  @Test
  void finds_a_suborder_by_its_short_description() {
    var order = order("MUSTER-01");
    var wanted = suborder(order, null, "S-01", "Wartung", false, null);
    suborder(order, null, "S-02", "Weiterentwicklung", false, null);

    assertThat(suborderIds("wartung", null)).containsExactly(wanted.getId());
  }

  @Test
  void finds_the_suborders_of_an_order_by_its_sign() {
    var order = order("MUSTER-01");
    var first = suborder(order, "S-01");
    var second = suborder(order, "S-02");
    suborder(order("ANDERE-02"), "S-03");

    assertThat(suborderIds("muster-01", null)).containsExactly(first.getId(), second.getId());
  }

  @Test
  void finds_the_suborders_of_an_order_by_its_short_description() {
    var wanted = suborder(order(customer, "A-01", "Wartungsvertrag", "Vorgang"), "S-01");
    suborder(order(customer, "A-02", "Weiterentwicklung", "Vorgang"), "S-02");

    assertThat(suborderIds("wartungsv", null)).containsExactly(wanted.getId());
  }

  @Test
  void finds_a_suborder_by_the_short_name_of_its_customer() {
    var wanted = suborder(order(customer("MK", "Musterkunde"), "A-01", null, "Vorgang"), "S-01");
    suborder(order("A-02"), "S-02");

    assertThat(suborderIds("mk", null)).containsExactly(wanted.getId());
  }

  @Test
  void requires_every_word_of_a_suborder_but_in_any_of_the_fields() {
    var muster = order("MUSTER-01");
    var wanted = suborder(muster, null, "S-01", "Wartung", false, null);
    suborder(muster, null, "S-02", "Weiterentwicklung", false, null);
    suborder(order("ANDERE-02"), null, "S-03", "Wartung", false, null);

    assertThat(suborderIds("muster wartung", null)).containsExactly(wanted.getId());
  }

  @Test
  void takes_an_underscore_in_a_suborder_sign_literally() {
    var order = order("MUSTER-01");
    var wanted = suborder(order, "S_1");
    suborder(order, "SX1");

    assertThat(suborderIds("s_1", null)).containsExactly(wanted.getId());
  }

  // --- suborders: hidden and ended ones last ---------------------------------------------------

  @Test
  void puts_ended_suborders_after_the_current_ones() {
    var order = order("MUSTER-01");
    var ended = suborder(order, null, "S-01", "Leistung", false, TODAY.minusDays(1));
    var endsToday = suborder(order, null, "S-02", "Leistung", false, TODAY);
    var hideNeverSet = suborder(order, null, "S-03", "Leistung", null, null);

    assertThat(suborderIds("leistung", null))
        .containsExactly(endsToday.getId(), hideNeverSet.getId(), ended.getId());
  }

  /**
   * The flag is not inherited in the data, but a suborder under a hidden order is no more on offer
   * than the order itself: it sinks with the hidden suborders.
   */
  @Test
  void puts_hidden_suborders_and_those_under_a_hidden_order_last() {
    var visibleOrder = order("A-ORDER");
    var hiddenOrder = order("B-ORDER", true, null);
    var hidden = suborder(visibleOrder, null, "S-01", "Leistung", true, null);
    var ended = suborder(visibleOrder, null, "S-02", "Leistung", false, TODAY.minusDays(1));
    var running = suborder(visibleOrder, null, "S-03", "Leistung", false, null);
    var underHiddenOrder = suborder(hiddenOrder, null, "S-04", "Leistung", false, null);

    assertThat(suborderIds("leistung", null))
        .containsExactly(running.getId(), ended.getId(), hidden.getId(), underHiddenOrder.getId());
  }

  @Test
  void cuts_hidden_and_ended_suborders_first_at_the_limit() {
    var order = order("MUSTER-01");
    suborder(order, null, "S-01", "Leistung", true, null);
    suborder(order, null, "S-02", "Leistung", false, TODAY.minusDays(1));
    var running = suborder(order, null, "S-03", "Leistung", false, null);

    assertThat(suborderRows("leistung", null, 1))
        .extracting(SuborderSearchRow::id)
        .containsExactly(running.getId());
  }

  /** The row carries both flags; the palette decides from them, not from the order of the rows. */
  @Test
  void reads_the_suborder_its_order_and_its_customer_into_one_row() {
    var musterkunde = customer("MK", "Musterkunde");
    var order = order(musterkunde, "MUSTER-01", "Vertrag", "Vorgang", true, null);
    var parent = suborder(order, "01");
    var child = suborder(order, parent, "02", "Wartung", false, TODAY.minusDays(1));

    assertThat(suborderRows("mk", null, PaletteQuery.CANDIDATE_LIMIT)).containsExactly(
        new SuborderSearchRow(parent.getId(), "01", "Leistung", null, order.getId(), "MUSTER-01",
            "Vertrag", "MK", false, true, null),
        new SuborderSearchRow(child.getId(), "02", "Wartung", parent.getId(), order.getId(), "MUSTER-01",
            "Vertrag", "MK", false, true, TODAY.minusDays(1)));
  }

  // --- suborders: narrowed to what a person may book today ------------------------------------

  /** The condition of the booking form: an employee order valid today, both ends inclusive. */
  @Test
  void narrows_to_the_suborders_with_an_employee_order_valid_today() {
    var order = order("MUSTER-01");
    var person = contract("ppp");
    var valid = suborder(order, "S-01");
    var endsToday = suborder(order, "S-02");
    var startsToday = suborder(order, "S-03");
    var ended = suborder(order, "S-04");
    var startsTomorrow = suborder(order, "S-05");
    suborder(order, "S-06");
    employeeorder(person, valid, LONG_AGO, null);
    employeeorder(person, endsToday, LONG_AGO, TODAY);
    employeeorder(person, startsToday, TODAY, TODAY.plusMonths(1));
    employeeorder(person, ended, LONG_AGO, TODAY.minusDays(1));
    employeeorder(person, startsTomorrow, TODAY.plusDays(1), null);

    assertThat(suborderIds("leistung", person.getEmployee().getId()))
        .containsExactly(valid.getId(), endsToday.getId(), startsToday.getId());
  }

  @Test
  void leaves_out_the_employee_orders_of_somebody_else() {
    var order = order("MUSTER-01");
    var person = contract("ppp");
    var other = contract("qqq");
    var own = suborder(order, "S-01");
    var foreign = suborder(order, "S-02");
    employeeorder(person, own, LONG_AGO, null);
    employeeorder(other, foreign, LONG_AGO, null);

    assertThat(suborderIds("leistung", person.getEmployee().getId())).containsExactly(own.getId());
  }

  /** {@code exists}, not a join: two employee orders on the same suborder do not double the row. */
  @Test
  void names_a_suborder_once_however_many_employee_orders_it_has() {
    var suborder = suborder(order("MUSTER-01"), "S-01");
    var person = contract("ppp");
    employeeorder(person, suborder, LONG_AGO, null);
    employeeorder(person, suborder, TODAY, TODAY);

    assertThat(suborderIds("leistung", person.getEmployee().getId())).containsExactly(suborder.getId());
  }

  @Test
  void leaves_every_suborder_in_without_a_person() {
    var order = order("MUSTER-01");
    var booked = suborder(order, "S-01");
    var unbooked = suborder(order, "S-02");
    employeeorder(contract("ppp"), booked, LONG_AGO, null);

    assertThat(suborderIds("leistung", null)).containsExactly(booked.getId(), unbooked.getId());
  }

  // --- the parent chain ------------------------------------------------------------------------

  @Test
  void reads_sign_and_parent_of_each_suborder_asked_for() {
    var order = order("MUSTER-01");
    var top = suborder(order, "01");
    var child = suborder(order, top, "02", "Leistung", false, null);
    suborder(order, child, "03", "Leistung", false, null);

    assertThat(suborderRepository.findSignRows(List.of(top.getId(), child.getId())))
        .containsExactlyInAnyOrder(
            new SuborderSignRow(top.getId(), "01", null),
            new SuborderSignRow(child.getId(), "02", top.getId()));
  }

  // --- helpers ---------------------------------------------------------------------------------

  private List<String> orderSigns(String typed) {
    return orderRows(typed, PaletteQuery.CANDIDATE_LIMIT).stream()
        .map(CustomerorderSearchRow::sign)
        .toList();
  }

  private List<CustomerorderSearchRow> orderRows(String typed, int limit) {
    var query = PaletteQuery.of(typed);
    entityManager.flush();
    return customerorderRepository.findPaletteCandidates(query.likeWord(0), query.likeWord(1),
        query.likeWord(2), TODAY, PageRequest.of(0, limit));
  }

  private List<Long> suborderIds(String typed, Long employeeId) {
    return suborderRows(typed, employeeId, PaletteQuery.CANDIDATE_LIMIT).stream()
        .map(SuborderSearchRow::id)
        .toList();
  }

  private List<SuborderSearchRow> suborderRows(String typed, Long employeeId, int limit) {
    var query = PaletteQuery.of(typed);
    entityManager.flush();
    return suborderRepository.findPaletteCandidates(query.likeWord(0), query.likeWord(1),
        query.likeWord(2), employeeId, TODAY, PageRequest.of(0, limit));
  }

  private Customer customer(String shortname, String name) {
    var created = new Customer();
    created.setShortname(shortname);
    created.setName(name);
    created.setAddress("Teststraße 1");
    return entityManager.persist(created);
  }

  private Customerorder order(String sign) {
    return order(customer, sign, null, "Vorgang", false, null);
  }

  private Customerorder order(String sign, Boolean hide, LocalDate untilDate) {
    return order(customer, sign, null, "Vorgang", hide, untilDate);
  }

  private Customerorder order(Customer orderCustomer, String sign, String shortdescription, String description) {
    return order(orderCustomer, sign, shortdescription, description, false, null);
  }

  /** {@code hide} stays {@code null} when passed so — the column is nullable, and that is a case. */
  private Customerorder order(Customer orderCustomer, String sign, String shortdescription,
      String description, Boolean hide, LocalDate untilDate) {
    var order = new Customerorder();
    order.setCustomer(orderCustomer);
    order.setSign(sign);
    order.setShortdescription(shortdescription);
    order.setDescription(description);
    order.setFromDate(LONG_AGO);
    order.setUntilDate(untilDate);
    order.setOrderType(OrderType.STANDARD);
    order.setDebithours(Duration.ZERO);
    order.setHide(hide);
    return entityManager.persist(order);
  }

  private Suborder suborder(Customerorder order, String sign) {
    return suborder(order, null, sign, "Leistung", false, null);
  }

  private Suborder suborder(Customerorder order, Suborder parent, String sign, String shortdescription,
      Boolean hide, LocalDate untilDate) {
    var suborder = new Suborder();
    suborder.setCustomerorder(order);
    suborder.setParentorder(parent);
    suborder.setSign(sign);
    suborder.setShortdescription(shortdescription);
    suborder.setDescription("Beschreibung");
    suborder.setFromDate(LONG_AGO);
    suborder.setUntilDate(untilDate);
    suborder.setDebithours(Duration.ZERO);
    suborder.setHide(hide);
    return entityManager.persist(suborder);
  }

  private Employeecontract contract(String sign) {
    var employee = new Employee();
    employee.setSign(sign);
    employee.setFirstname("Person");
    employee.setLastname(sign.toUpperCase());
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setHide(false);
    entityManager.persist(employee);

    var contract = new Employeecontract();
    contract.setEmployee(employee);
    contract.setValidFrom(LONG_AGO);
    contract.setDailyWorkingTime(Duration.ofHours(8));
    contract.setOvertimeStatic(Duration.ZERO);
    contract.setHide(false);
    return entityManager.persist(contract);
  }

  private void employeeorder(Employeecontract contract, Suborder suborder, LocalDate fromDate,
      LocalDate untilDate) {
    var employeeorder = new Employeeorder();
    employeeorder.setEmployeecontract(contract);
    employeeorder.setSuborder(suborder);
    employeeorder.setSign("eo-" + suborder.getSign());
    employeeorder.setFromDate(fromDate);
    employeeorder.setUntilDate(untilDate);
    employeeorder.setDebithours(Duration.ZERO);
    entityManager.persist(employeeorder);
  }
}
