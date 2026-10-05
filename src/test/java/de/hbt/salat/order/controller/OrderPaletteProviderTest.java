package de.hbt.salat.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static de.hbt.salat.common.palette.PaletteKind.CUSTOMERORDER;
import static de.hbt.salat.common.palette.PaletteKind.SUBORDER;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.palette.PaletteHit;
import de.hbt.salat.common.palette.PaletteKind;
import de.hbt.salat.common.palette.PaletteQuery;
import de.hbt.salat.common.palette.PaletteTarget;
import de.hbt.salat.common.palette.PaletteText;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.order.domain.CustomerorderSearchRow;
import de.hbt.salat.order.domain.SuborderSearchRow;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

/**
 * What the palette offers of orders and suborders, and where it leads, per role (#1157). A page of
 * its own exists only for managers — the edit forms are {@code requiresManager}. Everybody else who
 * is not restricted reaches an order or a suborder on its list page, narrowed down to it: the link
 * names every filter that could hide the object, an empty customer included, and switches on the
 * inactive or hidden ones only where the object needs it (ADR-0022). Restricted users see neither
 * list, so they get no order at all and only the suborders they may book today, without a target of
 * their own — the palette offers nothing whose page answers 403.
 *
 * <p>The strictness is lenient because every role stubs all its flags, whether the provider asks
 * for them or not: a role is what the user is, not what this code happens to read.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
@FixedClock("2026-06-25T10:15:30")
@DisplayNameGeneration(ReplaceUnderscores.class)
class OrderPaletteProviderTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 25);
  private static final PaletteQuery QUERY = PaletteQuery.of("muster");
  private static final long EMPLOYEE_ID = 7L;
  private static final long CUSTOMER_ID = 5L;
  private static final long ORDER_ID = 11L;
  private static final long SUBORDER_ID = 21L;
  private static final String ORDER_SIGN = "MUSTER-01";

  private static final PaletteText OPEN_ORDER = PaletteText.of("main.palette.target.customerorder.open");
  private static final PaletteText ORDER_SUBORDERS = PaletteText.of("main.palette.target.customerorder.suborders");
  private static final PaletteText OPEN_SUBORDER = PaletteText.of("main.palette.target.suborder.open");

  @Mock
  private CustomerorderService customerorderService;

  @Mock
  private SuborderService suborderService;

  @Mock
  private AuthorizedUser authorizedUser;

  @Mock
  private AuthorizedEmployee authorizedEmployee;

  @InjectMocks
  private OrderPaletteProvider provider;

  /** The four roles, with the flags {@code EmployeeStatusAuthorities} derives from their status. */
  enum Role {
    EMPLOYEE(false, false, false, false),
    PEOPLE_LEAD(false, false, true, false),
    MANAGER(false, true, true, true),
    RESTRICTED(true, false, false, false);

    private final boolean restricted;
    private final boolean manager;
    private final boolean peopleLead;
    private final boolean backoffice;

    Role(boolean restricted, boolean manager, boolean peopleLead, boolean backoffice) {
      this.restricted = restricted;
      this.manager = manager;
      this.peopleLead = peopleLead;
      this.backoffice = backoffice;
    }
  }

  // --- restricted -----------------------------------------------------------------------------

  @Test
  void offers_a_restricted_user_no_order() {
    loggedInAs(Role.RESTRICTED);
    givenOrders(order(false, null));
    givenSuborders(suborder(false, false, null));

    assertThat(provider.search(QUERY)).extracting(PaletteHit::kind).containsOnly(SUBORDER);
    verifyNoInteractions(customerorderService);
  }

  @Test
  void narrows_the_suborders_of_a_restricted_user_to_what_they_may_book_today() {
    loggedInAs(Role.RESTRICTED);
    givenSuborders(suborder(false, false, null));

    provider.search(QUERY);

    verify(suborderService).getPaletteCandidates(QUERY, EMPLOYEE_ID);
  }

  /** The booking module adds the target a restricted user can open, or the hit is not shown. */
  @Test
  void gives_the_suborder_of_a_restricted_user_no_target_of_its_own() {
    loggedInAs(Role.RESTRICTED);
    givenSuborders(suborder(false, false, null));

    var hit = onlyHit(SUBORDER);

    assertThat(hit.key()).isEqualTo(String.valueOf(SUBORDER_ID));
    assertThat(hit.targets()).isEmpty();
  }

  @Test
  void offers_a_restricted_user_without_an_employee_nothing() {
    loggedInAs(Role.RESTRICTED);
    when(authorizedEmployee.getEmployeeId()).thenReturn(null);
    givenOrders(order(false, null));
    givenSuborders(suborder(false, false, null));

    assertThat(provider.search(QUERY)).isEmpty();
    verifyNoInteractions(customerorderService, suborderService);
  }

  // --- manager --------------------------------------------------------------------------------

  @Test
  void opens_the_edit_form_of_an_order_for_a_manager() {
    loggedInAs(Role.MANAGER);
    givenOrders(order(false, null));

    assertThat(onlyHit(CUSTOMERORDER).targets()).containsExactly(
        new PaletteTarget(OPEN_ORDER, "/orders/customerorders/edit?id=11", PaletteTarget.OPEN),
        new PaletteTarget(ORDER_SUBORDERS, "/orders/suborders?fCustomerOrderId=11&fCustomerId=&fSuborderFilter=", 1));
  }

  @Test
  void opens_the_edit_form_of_a_suborder_for_a_manager() {
    loggedInAs(Role.MANAGER);
    givenSuborders(suborder(false, false, null));

    assertThat(onlyHit(SUBORDER).targets()).containsExactly(
        new PaletteTarget(OPEN_SUBORDER, "/orders/suborders/21/edit", PaletteTarget.OPEN));
  }

  /** The form shows the object whatever its state; no list switch belongs into its link. */
  @Test
  void opens_the_edit_form_of_an_ended_and_hidden_object_for_a_manager_as_it_is() {
    loggedInAs(Role.MANAGER);
    givenOrders(order(true, TODAY.minusDays(1)));
    givenSuborders(suborder(true, false, TODAY.minusDays(1)));

    assertThat(openHref(onlyHit(CUSTOMERORDER))).isEqualTo("/orders/customerorders/edit?id=11");
    assertThat(openHref(onlyHit(SUBORDER))).isEqualTo("/orders/suborders/21/edit");
  }

  // --- employee and people lead: the list pages -----------------------------------------------

  @ParameterizedTest
  @EnumSource(value = Role.class, names = {"EMPLOYEE", "PEOPLE_LEAD"})
  void opens_an_order_on_the_list_narrowed_to_its_sign_and_customer(Role role) {
    loggedInAs(role);
    givenOrders(order(false, null));

    assertThat(onlyHit(CUSTOMERORDER).targets()).containsExactly(
        new PaletteTarget(OPEN_ORDER, "/orders/customerorders?fCustomerOrderFilter=MUSTER-01&fCustomerId=5",
            PaletteTarget.OPEN),
        new PaletteTarget(ORDER_SUBORDERS, "/orders/suborders?fCustomerOrderId=11&fCustomerId=&fSuborderFilter=", 1));
  }

  @ParameterizedTest
  @EnumSource(value = Role.class, names = {"EMPLOYEE", "PEOPLE_LEAD"})
  void opens_an_ended_order_on_the_list_with_the_inactive_ones(Role role) {
    loggedInAs(role);
    givenOrders(order(false, TODAY.minusDays(1)));

    assertThat(openHref(onlyHit(CUSTOMERORDER))).isEqualTo(
        "/orders/customerorders?fCustomerOrderFilter=MUSTER-01&fCustomerId=5&fCustomerOrderShowInactive=true");
  }

  @ParameterizedTest
  @EnumSource(value = Role.class, names = {"EMPLOYEE", "PEOPLE_LEAD"})
  void opens_a_hidden_order_on_the_list_with_the_hidden_ones(Role role) {
    loggedInAs(role);
    givenOrders(order(true, null));

    assertThat(openHref(onlyHit(CUSTOMERORDER))).isEqualTo(
        "/orders/customerorders?fCustomerOrderFilter=MUSTER-01&fCustomerId=5&fCustomerOrderShowHidden=true");
  }

  /** An order ending today is still current (ADR-0029): no switch widens the list for it. */
  @ParameterizedTest
  @EnumSource(value = Role.class, names = {"EMPLOYEE", "PEOPLE_LEAD"})
  void opens_an_order_ending_today_on_the_list_without_a_switch(Role role) {
    loggedInAs(role);
    givenOrders(order(null, TODAY));

    assertThat(openHref(onlyHit(CUSTOMERORDER))).isEqualTo(
        "/orders/customerorders?fCustomerOrderFilter=MUSTER-01&fCustomerId=5");
  }

  @ParameterizedTest
  @EnumSource(value = Role.class, names = {"EMPLOYEE", "PEOPLE_LEAD"})
  void opens_a_suborder_on_the_list_of_its_order_narrowed_to_its_complete_sign(Role role) {
    loggedInAs(role);
    givenSuborders(suborder(false, false, null));

    assertThat(onlyHit(SUBORDER).targets()).containsExactly(new PaletteTarget(OPEN_SUBORDER,
        "/orders/suborders?fCustomerOrderId=11&fCustomerId=&fSuborderFilter=MUSTER-01%2F01", PaletteTarget.OPEN));
  }

  @ParameterizedTest
  @EnumSource(value = Role.class, names = {"EMPLOYEE", "PEOPLE_LEAD"})
  void opens_an_ended_suborder_on_the_list_with_the_inactive_ones(Role role) {
    loggedInAs(role);
    givenSuborders(suborder(false, false, TODAY.minusDays(1)));

    assertThat(openHref(onlyHit(SUBORDER))).isEqualTo(
        "/orders/suborders?fCustomerOrderId=11&fCustomerId=&fSuborderFilter=MUSTER-01%2F01&fSuborderShowInactive=true");
  }

  @ParameterizedTest
  @EnumSource(value = Role.class, names = {"EMPLOYEE", "PEOPLE_LEAD"})
  void opens_a_hidden_suborder_on_the_list_with_the_hidden_ones(Role role) {
    loggedInAs(role);
    givenSuborders(suborder(true, false, null));

    assertThat(openHref(onlyHit(SUBORDER))).isEqualTo(
        "/orders/suborders?fCustomerOrderId=11&fCustomerId=&fSuborderFilter=MUSTER-01%2F01&fSuborderShowHidden=true");
  }

  /**
   * The suborder list filters on the suborder's own flag only, so a suborder under a hidden order is
   * on it without the switch — the hit is marked hidden all the same.
   */
  @ParameterizedTest
  @EnumSource(value = Role.class, names = {"EMPLOYEE", "PEOPLE_LEAD"})
  void opens_a_suborder_under_a_hidden_order_on_the_list_without_the_hidden_switch(Role role) {
    loggedInAs(role);
    givenSuborders(suborder(false, true, null));

    assertThat(openHref(onlyHit(SUBORDER))).isEqualTo(
        "/orders/suborders?fCustomerOrderId=11&fCustomerId=&fSuborderFilter=MUSTER-01%2F01");
  }

  // --- every role that sees the lists ---------------------------------------------------------

  @ParameterizedTest
  @EnumSource(value = Role.class, names = {"EMPLOYEE", "PEOPLE_LEAD", "MANAGER"})
  void searches_the_orders_and_every_suborder_for_whoever_sees_the_lists(Role role) {
    loggedInAs(role);

    provider.search(QUERY);

    verify(customerorderService).getPaletteCandidates(QUERY);
    verify(suborderService).getPaletteCandidates(QUERY, null);
  }

  @ParameterizedTest
  @EnumSource(value = Role.class, names = {"EMPLOYEE", "PEOPLE_LEAD", "MANAGER"})
  void leads_from_an_ended_order_to_its_suborders_with_the_inactive_ones(Role role) {
    loggedInAs(role);
    givenOrders(order(false, TODAY.minusDays(1)));

    assertThat(onlyHit(CUSTOMERORDER).targets()).extracting(PaletteTarget::label, PaletteTarget::href)
        .contains(tuple(ORDER_SUBORDERS,
            "/orders/suborders?fCustomerOrderId=11&fCustomerId=&fSuborderFilter=&fSuborderShowInactive=true"));
  }

  // --- what a hit carries ---------------------------------------------------------------------

  /** The budget module keys its targets by the order's sign; the booking form takes a suborder's id. */
  @Test
  void keys_an_order_by_its_sign_and_a_suborder_by_its_id() {
    loggedInAs(Role.EMPLOYEE);
    givenOrders(order(false, null));
    givenSuborders(suborder(false, false, null));

    assertThat(provider.search(QUERY)).extracting(PaletteHit::kind, PaletteHit::key, PaletteHit::title)
        .containsExactly(
            tuple(CUSTOMERORDER, "MUSTER-01", "MUSTER-01"),
            tuple(SUBORDER, "21", "MUSTER-01/01"));
  }

  @Test
  void describes_an_order_by_its_short_description_and_its_customer() {
    loggedInAs(Role.EMPLOYEE);
    givenOrders(order(false, null));

    assertThat(onlyHit(CUSTOMERORDER).subtitle()).isEqualTo("Wartungsvertrag · MK");
  }

  /** Without a short description the description stands in, cut as the entity cuts it. */
  @Test
  void falls_back_to_the_shortened_description_and_the_customer_name() {
    loggedInAs(Role.EMPLOYEE);
    givenOrders(new CustomerorderSearchRow(ORDER_ID, ORDER_SIGN, " ",
        "Betrieb und Wartung der Anlagen am Standort Nord samt Bereitschaft", CUSTOMER_ID, "", "Musterkunde",
        false, null));

    assertThat(onlyHit(CUSTOMERORDER).subtitle())
        .isEqualTo("Betrieb und Wartung der Anlagen am Stand… · Musterkunde");
  }

  @Test
  void leaves_the_subtitle_of_an_order_out_when_nothing_describes_it() {
    loggedInAs(Role.EMPLOYEE);
    givenOrders(new CustomerorderSearchRow(ORDER_ID, ORDER_SIGN, null, null, CUSTOMER_ID, null, null, false, null));

    assertThat(onlyHit(CUSTOMERORDER).subtitle()).isNull();
  }

  @Test
  void describes_a_suborder_by_its_short_description_and_names_its_customer() {
    loggedInAs(Role.EMPLOYEE);
    givenSuborders(suborder(false, false, null));

    var hit = onlyHit(SUBORDER);

    assertThat(hit.subtitle()).isEqualTo("Wartung");
    assertThat(hit.context()).isEqualTo(PaletteText.of("main.palette.context.plain", "MK"));
  }

  @Test
  void leaves_the_context_of_a_suborder_out_without_a_customer_short_name() {
    loggedInAs(Role.EMPLOYEE);
    givenSuborders(new SuborderSearchRow(SUBORDER_ID, "01", "Wartung", ORDER_SIGN + "/01", ORDER_ID, ORDER_SIGN,
        "Wartungsvertrag", null, false, false, null));

    assertThat(onlyHit(SUBORDER).context()).isNull();
  }

  /** Ended means an end before today; an end today and an open end are current (ADR-0029). */
  @Test
  void marks_an_order_as_ended_only_after_its_last_day() {
    loggedInAs(Role.EMPLOYEE);
    givenOrders(order("ENDED", false, TODAY.minusDays(1)), order("ENDS-TODAY", false, TODAY),
        order("OPEN-END", false, null));

    assertThat(hits(CUSTOMERORDER)).extracting(PaletteHit::key, PaletteHit::ended)
        .containsExactly(tuple("ENDED", true), tuple("ENDS-TODAY", false), tuple("OPEN-END", false));
  }

  /** A {@code hide} never set is not hidden (#1104). */
  @Test
  void marks_an_order_as_hidden_only_when_its_flag_says_so() {
    loggedInAs(Role.EMPLOYEE);
    givenOrders(order("HIDDEN", true, null), order("VISIBLE", false, null), order("NEVER-SET", null, null));

    assertThat(hits(CUSTOMERORDER)).extracting(PaletteHit::key, PaletteHit::hidden)
        .containsExactly(tuple("HIDDEN", true), tuple("VISIBLE", false), tuple("NEVER-SET", false));
  }

  /** A suborder under a hidden order is no more on offer than the order itself. */
  @Test
  void marks_a_suborder_as_hidden_when_it_or_its_order_is() {
    loggedInAs(Role.EMPLOYEE);
    givenSuborders(
        suborder(21L, "01", true, false, null),
        suborder(22L, "02", false, true, null),
        suborder(23L, "03", false, false, null),
        suborder(24L, "04", null, null, null));

    assertThat(hits(SUBORDER)).extracting(PaletteHit::key, PaletteHit::hidden)
        .containsExactly(tuple("21", true), tuple("22", true), tuple("23", false), tuple("24", false));
  }

  @Test
  void marks_a_suborder_as_ended_only_after_its_last_day() {
    loggedInAs(Role.EMPLOYEE);
    givenSuborders(
        suborder(21L, "01", false, false, TODAY.minusDays(1)),
        suborder(22L, "02", false, false, TODAY),
        suborder(23L, "03", false, false, null));

    assertThat(hits(SUBORDER)).extracting(PaletteHit::key, PaletteHit::ended)
        .containsExactly(tuple("21", true), tuple("22", false), tuple("23", false));
  }

  /** The title beginning with the query ranks highest; the customer's name counts as a field too. */
  @Test
  void ranks_a_hit_by_the_fields_it_shows() {
    loggedInAs(Role.EMPLOYEE);
    givenOrders(order(false, null));
    givenSuborders(suborder(false, false, null));

    assertThat(provider.search(PaletteQuery.of("muster"))).extracting(PaletteHit::match).containsExactly(4, 4);
    assertThat(provider.search(PaletteQuery.of("musterkunde")))
        .filteredOn(hit -> hit.kind() == CUSTOMERORDER)
        .extracting(PaletteHit::match)
        .containsExactly(3);
  }

  // --- helpers ---------------------------------------------------------------------------------

  private void loggedInAs(Role role) {
    when(authorizedUser.isRestricted()).thenReturn(role.restricted);
    when(authorizedUser.isManager()).thenReturn(role.manager);
    when(authorizedUser.isPeopleLead()).thenReturn(role.peopleLead);
    when(authorizedUser.isBackoffice()).thenReturn(role.backoffice);
    when(authorizedEmployee.getEmployeeId()).thenReturn(EMPLOYEE_ID);
  }

  private void givenOrders(CustomerorderSearchRow... rows) {
    when(customerorderService.getPaletteCandidates(any())).thenReturn(List.of(rows));
  }

  private void givenSuborders(SuborderSearchRow... rows) {
    when(suborderService.getPaletteCandidates(any(), any())).thenReturn(List.of(rows));
  }

  private List<PaletteHit> hits(PaletteKind kind) {
    return provider.search(QUERY).stream().filter(hit -> hit.kind() == kind).toList();
  }

  private PaletteHit onlyHit(PaletteKind kind) {
    var hits = hits(kind);
    assertThat(hits).hasSize(1);
    return hits.getFirst();
  }

  private static String openHref(PaletteHit hit) {
    return hit.targets().stream()
        .filter(target -> target.rank() == PaletteTarget.OPEN)
        .map(PaletteTarget::href)
        .findFirst()
        .orElseThrow();
  }

  private static CustomerorderSearchRow order(Boolean hide, LocalDate untilDate) {
    return order(ORDER_SIGN, hide, untilDate);
  }

  private static CustomerorderSearchRow order(String sign, Boolean hide, LocalDate untilDate) {
    return new CustomerorderSearchRow(ORDER_ID, sign, "Wartungsvertrag", "Wartung und Betrieb", CUSTOMER_ID,
        "MK", "Musterkunde", hide, untilDate);
  }

  private static SuborderSearchRow suborder(Boolean hide, Boolean orderHide, LocalDate untilDate) {
    return suborder(SUBORDER_ID, "01", hide, orderHide, untilDate);
  }

  private static SuborderSearchRow suborder(long id, String sign, Boolean hide, Boolean orderHide,
      LocalDate untilDate) {
    return new SuborderSearchRow(id, sign, "Wartung", ORDER_SIGN + "/" + sign, ORDER_ID, ORDER_SIGN, "Wartungsvertrag", "MK",
        hide, orderHide, untilDate);
  }
}
