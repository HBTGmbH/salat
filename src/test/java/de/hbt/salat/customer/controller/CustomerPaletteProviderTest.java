package de.hbt.salat.customer.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static de.hbt.salat.common.palette.PaletteKind.CUSTOMER;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.palette.PaletteHit;
import de.hbt.salat.common.palette.PaletteQuery;
import de.hbt.salat.common.palette.PaletteTarget;
import de.hbt.salat.common.palette.PaletteText;
import de.hbt.salat.customer.domain.CustomerSearchRow;
import de.hbt.salat.customer.service.CustomerService;

/**
 * What the command palette offers of a customer, per role (#1157). The palette offers nothing whose
 * page would answer 403: restricted users see no customer list and get no hit, the service is not
 * even asked. Only a manager has a page for the single customer, the edit form; everybody else
 * opens the list narrowed to the short name, with hidden customers shown only where the hit is one.
 * "Its orders" is the order list for this customer, with the order filter emptied — a filter left
 * out of the link would be supplied from the remembered state and could hide the orders (ADR-0022).
 *
 * <p>The roles are stubbed as {@code EmployeeStatusAuthorities} grants them: a manager is people
 * lead and backoffice as well. The mocks are lenient because the provider asks only two of the
 * four flags.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class CustomerPaletteProviderTest {

  private static final PaletteQuery QUERY = PaletteQuery.of("muster");
  private static final long ID = 7L;
  private static final String ORDERS_OF_ID = "/orders/customerorders?fCustomerId=7&fCustomerOrderFilter=";

  enum Role { EMPLOYEE, PEOPLE_LEAD, MANAGER, RESTRICTED }

  @Mock
  private CustomerService customerService;

  @Mock
  private AuthorizedUser authorizedUser;

  @InjectMocks
  private CustomerPaletteProvider provider;

  // --- who gets a hit at all -----------------------------------------------------------------

  @Test
  void a_restricted_user_gets_no_customer_and_the_service_is_not_asked() {
    loginAs(Role.RESTRICTED);
    candidates(row("MUSTER", "Musterkunde GmbH", false));

    assertThat(provider.search(QUERY)).isEmpty();
    verifyNoInteractions(customerService);
  }

  @ParameterizedTest
  @EnumSource(value = Role.class, names = "RESTRICTED", mode = EXCLUDE)
  void every_other_role_gets_the_customer_with_the_query_it_typed(Role role) {
    loginAs(role);
    candidates(row("MUSTER", "Musterkunde GmbH", false));

    assertThat(provider.search(QUERY)).hasSize(1);
    verify(customerService).getPaletteCandidates(QUERY);
  }

  @Test
  void gives_one_hit_per_candidate_in_the_order_of_the_service() {
    loginAs(Role.EMPLOYEE);
    candidates(
        new CustomerSearchRow(2L, "MUSTER-B", "Musterkunde B", false),
        new CustomerSearchRow(1L, "MUSTER-A", "Musterkunde A", false));

    assertThat(provider.search(QUERY)).extracting(PaletteHit::key).containsExactly("2", "1");
  }

  // --- the target that opens the customer ----------------------------------------------------

  @Test
  void a_manager_opens_the_edit_form_of_the_customer() {
    loginAs(Role.MANAGER);
    candidates(row("MUSTER", "Musterkunde GmbH", false));

    assertThat(openHref(single())).isEqualTo("/customers/edit?id=7");
  }

  /** The edit form shows a hidden customer as it shows any other; no switch is needed. */
  @Test
  void a_manager_opens_the_edit_form_of_a_hidden_customer_as_well() {
    loginAs(Role.MANAGER);
    candidates(row("MUSTER", "Musterkunde GmbH", true));

    assertThat(openHref(single())).isEqualTo("/customers/edit?id=7");
  }

  /** The edit form answers 403 without the manager role, so the list is the page to open. */
  @ParameterizedTest
  @EnumSource(value = Role.class, names = {"EMPLOYEE", "PEOPLE_LEAD"})
  void without_the_manager_role_the_list_opens_narrowed_to_the_short_name(Role role) {
    loginAs(role);
    candidates(row("MUSTER", "Musterkunde GmbH", false));

    assertThat(openHref(single())).isEqualTo("/customers?fCustomerFilter=MUSTER");
  }

  /** The list leaves hidden customers out unless asked — the hit would be missing from it. */
  @ParameterizedTest
  @EnumSource(value = Role.class, names = {"EMPLOYEE", "PEOPLE_LEAD"})
  void the_list_shows_hidden_customers_when_the_hit_is_hidden(Role role) {
    loginAs(role);
    candidates(row("MUSTER", "Musterkunde GmbH", true));

    assertThat(openHref(single()))
        .isEqualTo("/customers?fCustomerFilter=MUSTER&fCustomerShowHidden=true");
  }

  /** {@code hide} never written is not hidden ({@link de.hbt.salat.common.Hiding}). */
  @Test
  void a_customer_whose_hide_flag_was_never_set_is_not_hidden() {
    loginAs(Role.EMPLOYEE);
    candidates(row("MUSTER", "Musterkunde GmbH", null));

    var hit = single();

    assertThat(hit.hidden()).isFalse();
    assertThat(openHref(hit)).isEqualTo("/customers?fCustomerFilter=MUSTER");
  }

  @Test
  void the_short_name_is_encoded_in_the_filter() {
    loginAs(Role.EMPLOYEE);
    candidates(row("M&S 01/A", "Muster und Sohn", false));

    assertThat(openHref(single())).isEqualTo("/customers?fCustomerFilter=M%26S+01%2FA");
  }

  // --- its orders ----------------------------------------------------------------------------

  @ParameterizedTest
  @EnumSource(value = Role.class, names = "RESTRICTED", mode = EXCLUDE)
  void every_role_that_sees_customers_gets_the_customer_and_its_orders(Role role) {
    loginAs(role);
    candidates(row("MUSTER", "Musterkunde GmbH", false));

    var targets = single().targets();

    assertThat(targets).hasSize(2);
    assertThat(targets.get(0).label()).isEqualTo(PaletteText.of("main.palette.target.customer.open"));
    assertThat(targets.get(0).rank()).isEqualTo(PaletteTarget.OPEN);
    assertThat(targets.get(1)).isEqualTo(new PaletteTarget(
        PaletteText.of("main.palette.target.customer.orders"), ORDERS_OF_ID, 1));
  }

  /** The orders of a hidden customer are hidden as a rule; without the switch the list stays empty. */
  @Test
  void the_orders_of_a_hidden_customer_are_shown_with_the_hidden_ones() {
    loginAs(Role.EMPLOYEE);
    candidates(row("MUSTER", "Musterkunde GmbH", true));

    assertThat(single().targets()).extracting(PaletteTarget::href)
        .contains(ORDERS_OF_ID + "&fCustomerOrderShowHidden=true");
  }

  @Test
  void a_customer_without_short_name_and_name_is_left_out() {
    loginAs(Role.EMPLOYEE);
    candidates(row(" ", null, false));

    assertThat(provider.search(QUERY)).isEmpty();
  }

  // --- what the hit shows --------------------------------------------------------------------

  @Test
  void the_hit_is_a_customer_keyed_by_its_id_and_marked_when_hidden() {
    loginAs(Role.MANAGER);
    candidates(row("MUSTER", "Musterkunde GmbH", true));

    var hit = single();

    assertThat(hit.kind()).isEqualTo(CUSTOMER);
    assertThat(hit.key()).isEqualTo("7");
    assertThat(hit.hidden()).isTrue();
    assertThat(hit.ended()).isFalse();
    assertThat(hit.context()).isNull();
  }

  @Test
  void the_name_is_the_subtitle_where_it_differs_from_the_short_name() {
    loginAs(Role.EMPLOYEE);
    candidates(row("MUSTER", "Musterkunde GmbH", false));

    var hit = single();

    assertThat(hit.title()).isEqualTo("MUSTER");
    assertThat(hit.subtitle()).isEqualTo("Musterkunde GmbH");
  }

  @Test
  void a_name_equal_to_the_short_name_is_not_repeated_as_subtitle() {
    loginAs(Role.EMPLOYEE);
    candidates(row("Musterkunde", "Musterkunde", false));

    var hit = single();

    assertThat(hit.title()).isEqualTo("Musterkunde");
    assertThat(hit.subtitle()).isNull();
  }

  /** A customer stored without a short name is shown and filtered by its full name. */
  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "  ")
  void a_customer_without_a_short_name_is_shown_and_filtered_by_its_name(String shortname) {
    loginAs(Role.EMPLOYEE);
    candidates(row(shortname, "Musterkunde GmbH", false));

    var hit = single();

    assertThat(hit.title()).isEqualTo("Musterkunde GmbH");
    assertThat(hit.subtitle()).isNull();
    assertThat(openHref(hit)).isEqualTo("/customers?fCustomerFilter=Musterkunde+GmbH");
  }

  /** The name counts for the ranking too: "beispiel" begins a word of the name, not of the title. */
  @Test
  void the_name_counts_for_how_well_the_hit_matches() {
    loginAs(Role.EMPLOYEE);
    candidates(row("MK-01", "Muster Beispiel GmbH", false));

    var hits = provider.search(PaletteQuery.of("beispiel"));

    assertThat(hits).extracting(PaletteHit::match).containsExactly(3);
  }

  private void loginAs(Role role) {
    when(authorizedUser.isRestricted()).thenReturn(role == Role.RESTRICTED);
    when(authorizedUser.isManager()).thenReturn(role == Role.MANAGER);
    when(authorizedUser.isPeopleLead()).thenReturn(role == Role.MANAGER || role == Role.PEOPLE_LEAD);
    when(authorizedUser.isBackoffice()).thenReturn(role == Role.MANAGER);
  }

  private void candidates(CustomerSearchRow... rows) {
    when(customerService.getPaletteCandidates(any())).thenReturn(List.of(rows));
  }

  private static CustomerSearchRow row(String shortname, String name, Boolean hide) {
    return new CustomerSearchRow(ID, shortname, name, hide);
  }

  private PaletteHit single() {
    var hits = provider.search(QUERY);
    assertThat(hits).hasSize(1);
    return hits.getFirst();
  }

  private static String openHref(PaletteHit hit) {
    return hit.targets().stream()
        .filter(target -> target.rank() == PaletteTarget.OPEN)
        .findFirst()
        .orElseThrow()
        .href();
  }
}
