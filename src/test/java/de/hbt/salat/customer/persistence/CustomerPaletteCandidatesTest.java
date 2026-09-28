package de.hbt.salat.customer.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.common.palette.PaletteQuery;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.customer.domain.CustomerSearchRow;

/**
 * The customers the command palette considers for a query (#1157),
 * {@link CustomerRepository#findPaletteCandidates}. Every typed word has to stand in the short name
 * or in the name, case ignored on both sides: the patterns come from {@link PaletteQuery} in lower
 * case, and the columns are lowered in the query because their collation is not the same in every
 * schema.
 *
 * <p>Hidden customers stay in and come last, so that the candidate limit drops them first; a
 * {@code hide} never written counts as not hidden ({@link de.hbt.salat.common.Hiding}). A {@code %},
 * {@code _} or {@code !} typed into the palette is a character, not a wildcard — the patterns are
 * built by {@link PaletteQuery#likeWord}, exactly as the service builds them.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class CustomerPaletteCandidatesTest {

  @Autowired
  private CustomerRepository customerRepository;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
  }

  // --- short name and name --------------------------------------------------------------------

  @Test
  void finds_a_customer_by_part_of_its_short_name() {
    customer("MUSTER-01", "Musterkunde GmbH", false);
    customer("ANDERS", "Anders AG", false);

    assertThat(shortnames("ter-0")).containsExactly("MUSTER-01");
  }

  @Test
  void finds_a_customer_by_part_of_its_name() {
    customer("MK", "Musterkunde GmbH", false);
    customer("ANDERS", "Anders AG", false);

    assertThat(shortnames("kunde")).containsExactly("MK");
  }

  /**
   * The query lowers the columns: the stored {@code MUSTER-B} is found by the lower-case pattern.
   * Sorted case-sensitively, {@code MUSTER-B} would stand before {@code muster-a}.
   */
  @Test
  void ignores_case_and_orders_by_short_name_ignoring_case() {
    customer("MUSTER-B", "Zweite AG", false);
    customer("muster-a", "Erste AG", false);

    assertThat(shortnames("Muster")).containsExactly("muster-a", "MUSTER-B");
    assertThat(shortnames("ZWEITE")).containsExactly("MUSTER-B");
  }

  /**
   * The row carries the column, not {@link Customer#getShortname()} with its fallback — the provider
   * falls back to the name itself.
   */
  @Test
  void finds_a_customer_without_a_short_name_by_its_name() {
    var saved = customer(null, "Musterkunde ohne Kurzname", false);

    var rows = candidates("ohne", PaletteQuery.CANDIDATE_LIMIT);

    assertThat(rows).containsExactly(
        new CustomerSearchRow(saved.getId(), null, "Musterkunde ohne Kurzname", false));
  }

  @Test
  void finds_nothing_for_a_word_no_customer_contains() {
    customer("MUSTER-01", "Musterkunde GmbH", false);

    assertThat(shortnames("xyz")).isEmpty();
  }

  // --- several words ----------------------------------------------------------------------------

  /** One word in the short name, the other in the name — each has to stand somewhere. */
  @Test
  void every_word_has_to_match_the_short_name_or_the_name() {
    customer("MUSTER-01", "Nordlicht Beispiel GmbH", false);
    customer("MUSTER-02", "Suedwind Beispiel GmbH", false);
    customer("NORD", "Anders AG", false);

    assertThat(shortnames("muster nord")).containsExactly("MUSTER-01");
  }

  @Test
  void the_order_of_the_words_does_not_count() {
    customer("MUSTER-01", "Nordlicht Beispiel GmbH", false);
    customer("MUSTER-02", "Suedwind Beispiel GmbH", false);

    assertThat(shortnames("nord muster")).containsExactly("MUSTER-01");
  }

  @Test
  void the_third_word_counts_as_well() {
    customer("MUSTER-01", "Nordlicht Beispiel GmbH", false);
    customer("MUSTER-02", "Suedwind Beispiel GmbH", false);

    assertThat(shortnames("muster beispiel wind")).containsExactly("MUSTER-02");
  }

  // --- hidden customers -----------------------------------------------------------------------

  /**
   * Hidden ones stay in, ordered after the others whatever their short name; {@code hide} reaches
   * the row as stored, {@code null} included.
   */
  @Test
  void keeps_hidden_customers_and_orders_them_last() {
    customer("MUSTER-A", "Musterkunde A", true);
    customer("MUSTER-B", "Musterkunde B", false);
    customerWithoutHideFlag("MUSTER-C", "Musterkunde C");

    var rows = candidates("muster", PaletteQuery.CANDIDATE_LIMIT);

    assertThat(rows).extracting(CustomerSearchRow::shortname)
        .containsExactly("MUSTER-B", "MUSTER-C", "MUSTER-A");
    assertThat(rows).extracting(CustomerSearchRow::hide).containsExactly(false, null, true);
  }

  /** The limit applies after the ordering, so the hidden customer is the one left out. */
  @Test
  void the_limit_drops_the_hidden_customers_first() {
    customer("MUSTER-A", "Musterkunde A", true);
    customer("MUSTER-B", "Musterkunde B", false);
    customerWithoutHideFlag("MUSTER-C", "Musterkunde C");

    assertThat(candidates("muster", 2)).extracting(CustomerSearchRow::shortname)
        .containsExactly("MUSTER-B", "MUSTER-C");
  }

  // --- wildcards of LIKE ----------------------------------------------------------------------

  /** Unescaped, {@code %50%%} would find {@code RABATT-500} too. */
  @Test
  void a_percent_sign_is_a_character_not_a_wildcard() {
    customer("RABATT-50%", "Rabatt Eins AG", false);
    customer("RABATT-500", "Rabatt Zwei AG", false);

    assertThat(shortnames("50%")).containsExactly("RABATT-50%");
  }

  /** Unescaped, the underscore would stand for the hyphen of {@code MUSTER-01}. */
  @Test
  void an_underscore_is_a_character_not_a_wildcard() {
    customer("MUSTER_01", "Unterstrich AG", false);
    customer("MUSTER-01", "Bindestrich AG", false);

    assertThat(shortnames("r_0")).containsExactly("MUSTER_01");
  }

  @Test
  void the_escape_character_itself_is_a_character() {
    customer("HALLO!", "Ausruf AG", false);
    customer("HALLO", "Ohne Zeichen AG", false);

    assertThat(shortnames("lo!")).containsExactly("HALLO!");
  }

  private List<String> shortnames(String typed) {
    return candidates(typed, PaletteQuery.CANDIDATE_LIMIT).stream()
        .map(CustomerSearchRow::shortname)
        .toList();
  }

  private List<CustomerSearchRow> candidates(String typed, int limit) {
    var query = PaletteQuery.of(typed);
    return customerRepository.findPaletteCandidates(query.likeWord(0), query.likeWord(1),
        query.likeWord(2), PageRequest.of(0, limit));
  }

  private Customer customer(String shortname, String name, boolean hidden) {
    var customer = new Customer();
    customer.setShortname(shortname);
    customer.setName(name);
    customer.setAddress("Musterstraße 1");
    customer.setHide(hidden);
    return customerRepository.save(customer);
  }

  /** Like {@link #customer}, but leaves {@code hide} unset — the column is nullable. */
  private Customer customerWithoutHideFlag(String shortname, String name) {
    var customer = new Customer();
    customer.setShortname(shortname);
    customer.setName(name);
    customer.setAddress("Musterstraße 1");
    return customerRepository.save(customer);
  }
}
