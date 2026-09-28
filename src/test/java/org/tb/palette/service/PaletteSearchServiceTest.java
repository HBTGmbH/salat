package org.tb.palette.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.tb.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static org.tb.common.palette.PaletteKind.CUSTOMER;
import static org.tb.common.palette.PaletteKind.CUSTOMERORDER;
import static org.tb.common.palette.PaletteKind.PERSON;
import static org.tb.common.palette.PaletteKind.SUBORDER;
import static org.tb.common.palette.PaletteQuery.HITS_PER_KIND;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.palette.PaletteCommand;
import org.tb.common.palette.PaletteHit;
import org.tb.common.palette.PaletteKind;
import org.tb.common.palette.PaletteParameter;
import org.tb.common.palette.PaletteProvider;
import org.tb.common.palette.PaletteQuery;
import org.tb.common.palette.PaletteSuggestion;
import org.tb.common.palette.PaletteSuggestionRequest;
import org.tb.common.palette.PaletteTarget;
import org.tb.common.palette.PaletteText;

/**
 * How the palette puts the answers of the modules together (#1157, ADR-0031), with small fake
 * providers in place of the modules: merging hits of one object, ranking current ones before ended
 * and hidden ones, the targets other modules add, and the limit per kind.
 *
 * <p>What a user may see is not decided here but by each provider, so there are no roles in this
 * test. What is decided here is that a provider denied by an {@link AuthorizationException}
 * contributes nothing and takes nobody else's hits along, and that no hit is offered without a
 * target to open.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class PaletteSearchServiceTest {

  // --- asking the providers -----------------------------------------------------------------------

  @Test
  void asks_no_provider_below_two_characters() {
    var provider = new FakeProvider().finds(hit(CUSTOMERORDER, "MUSTER-01", open("MUSTER-01")));
    var service = service(provider);

    assertThat(service.search("m")).isEmpty();
    assertThat(service.search("  m  ")).isEmpty();
    assertThat(service.search(null)).isEmpty();
    assertThat(provider.queries).isEmpty();
    assertThat(provider.offered).isEmpty();

    assertThat(service.search("mu")).isNotEmpty();
  }

  @Test
  void hands_every_provider_the_prepared_query() {
    var first = new FakeProvider();
    var second = new FakeProvider();

    service(first, second).search("  MUSTER   01 ");

    assertThat(first.queries).extracting(PaletteQuery::text).containsExactly("muster 01");
    assertThat(second.queries).extracting(PaletteQuery::text).containsExactly("muster 01");
  }

  @Test
  void asks_for_no_targets_where_nothing_was_found() {
    var provider = new FakeProvider();

    assertThat(service(provider).search("muster")).isEmpty();
    assertThat(provider.offered).isEmpty();
  }

  // --- one object, several providers --------------------------------------------------------------

  @Test
  void merges_the_hits_of_one_object_and_concatenates_their_targets() {
    var first = new FakeProvider().finds(hit(CUSTOMERORDER, "MUSTER-01", target("/open", PaletteTarget.OPEN)));
    var second = new FakeProvider().finds(hit(CUSTOMERORDER, "MUSTER-01", target("/more", 1)));

    var hit = onlyHit(service(first, second).search("muster"), CUSTOMERORDER);

    assertThat(hrefs(hit)).containsExactly("/open", "/more");
  }

  @Test
  void keeps_the_same_key_of_two_kinds_apart() {
    var provider = new FakeProvider().finds(
        hit(CUSTOMERORDER, "MUSTER", target("/order", PaletteTarget.OPEN)),
        hit(CUSTOMER, "MUSTER", target("/customer", PaletteTarget.OPEN)));

    var groups = service(provider).search("muster");

    assertThat(hrefs(onlyHit(groups, CUSTOMERORDER))).containsExactly("/order");
    assertThat(hrefs(onlyHit(groups, CUSTOMER))).containsExactly("/customer");
  }

  /** The budget module is asked after every search, wherever it stands among the providers. */
  @Test
  void adds_the_targets_of_other_providers_and_orders_all_targets_by_rank() {
    var budget = new FakeProvider()
        .adds(CUSTOMERORDER, "MUSTER-01", target("/budget", 3), target("/controlling", 2))
        .adds(CUSTOMERORDER, "MUSTER-99", target("/elsewhere", PaletteTarget.OPEN));
    var orders = new FakeProvider().finds(hit(CUSTOMERORDER, "MUSTER-01",
        target("/suborders", 1), target("/open", PaletteTarget.OPEN)));

    var hit = onlyHit(service(budget, orders).search("muster"), CUSTOMERORDER);

    assertThat(hrefs(hit)).containsExactly("/open", "/suborders", "/controlling", "/budget");
  }

  /** A restricted user's suborder: the order module offers nothing, the booking module the booking. */
  @Test
  void keeps_a_hit_whose_only_targets_come_from_another_provider() {
    var orders = new FakeProvider().finds(hit(SUBORDER, "MUSTER-01.03"));
    var bookings = new FakeProvider().adds(SUBORDER, "MUSTER-01.03", target("/book", 1));

    var hit = onlyHit(service(orders, bookings).search("muster"), SUBORDER);

    assertThat(hrefs(hit)).containsExactly("/book");
  }

  @Test
  void offers_every_provider_the_candidates_of_each_kind_separately() {
    var orders = new FakeProvider().finds(
        hit(CUSTOMERORDER, "MUSTER-01", open("MUSTER-01")),
        hit(SUBORDER, "MUSTER-01.03", open("MUSTER-01.03")));
    var budget = new FakeProvider();

    service(orders, budget).search("muster");

    assertThat(budget.offered).containsOnlyKeys(CUSTOMERORDER, SUBORDER);
    assertThat(budget.offered.get(CUSTOMERORDER)).extracting(PaletteHit::title).containsExactly("MUSTER-01");
    assertThat(budget.offered.get(SUBORDER)).extracting(PaletteHit::title).containsExactly("MUSTER-01.03");
  }

  // --- ranking and limits -------------------------------------------------------------------------

  /** Hidden is the first criterion: a hidden order ranks below an ended one, however well it matches. */
  @Test
  void ranks_current_hits_before_ended_ones_and_those_before_hidden_ones() {
    var provider = new FakeProvider().finds(
        ranked("hidden-and-ended", true, true, 4),
        ranked("hidden", false, true, 4),
        ranked("ended", true, false, 4),
        ranked("current", false, false, 1));

    assertThat(titles(service(provider).search("muster"), CUSTOMERORDER))
        .containsExactly("current", "ended", "hidden", "hidden-and-ended");
  }

  @Test
  void ranks_by_match_and_then_by_title_ignoring_the_case() {
    var provider = new FakeProvider().finds(
        ranked("beta", false, false, 3),
        ranked("epsilon", false, false, 1),
        ranked("Gamma", false, false, 3),
        ranked("delta", false, false, 4),
        ranked("Alpha", false, false, 3));

    assertThat(titles(service(provider).search("muster"), CUSTOMERORDER))
        .containsExactly("delta", "Alpha", "beta", "Gamma", "epsilon");
  }

  @Test
  void shows_at_most_five_hits_of_each_kind() {
    var signs = signs(HITS_PER_KIND + 3);
    var provider = new FakeProvider()
        .finds(signs.reversed().stream().map(sign -> hit(CUSTOMERORDER, sign, open(sign))).toList())
        .finds(hit(CUSTOMER, "MUSTERKUNDE", open("MUSTERKUNDE")));

    var groups = service(provider).search("muster");

    assertThat(titles(groups, CUSTOMERORDER)).containsExactlyElementsOf(signs.subList(0, HITS_PER_KIND));
    assertThat(titles(groups, CUSTOMER)).containsExactly("MUSTERKUNDE");
  }

  @Test
  void offers_the_other_providers_the_best_candidates_twice_as_many_as_are_shown() {
    var signs = signs(2 * HITS_PER_KIND + 2);
    var orders = new FakeProvider()
        .finds(signs.reversed().stream().map(sign -> hit(CUSTOMERORDER, sign, open(sign))).toList());
    var budget = new FakeProvider();

    service(orders, budget).search("muster");

    assertThat(budget.offered.get(CUSTOMERORDER)).extracting(PaletteHit::title)
        .containsExactlyElementsOf(signs.subList(0, 2 * HITS_PER_KIND));
  }

  // --- nothing that cannot be opened --------------------------------------------------------------

  @Test
  void drops_a_hit_without_any_target_and_lets_the_next_candidate_move_up() {
    var signs = signs(HITS_PER_KIND + 3);
    var withoutTarget = signs.subList(0, 3);
    var provider = new FakeProvider().finds(signs.stream()
        .map(sign -> withoutTarget.contains(sign) ? hit(CUSTOMERORDER, sign) : hit(CUSTOMERORDER, sign, open(sign)))
        .toList());

    assertThat(titles(service(provider).search("muster"), CUSTOMERORDER))
        .containsExactlyElementsOf(signs.subList(3, HITS_PER_KIND + 3));
  }

  /** Only the candidates are offered for further targets, so only they can be shown. */
  @Test
  void looks_no_further_than_the_candidates_for_hits_to_show() {
    var candidates = 2 * HITS_PER_KIND;
    var signs = signs(candidates + 2);
    var provider = new FakeProvider().finds(signs.stream()
        .map(sign -> signs.indexOf(sign) < candidates ? hit(CUSTOMERORDER, sign) : hit(CUSTOMERORDER, sign, open(sign)))
        .toList());

    assertThat(service(provider).search("muster")).isEmpty();
  }

  @Test
  void shows_no_group_for_a_kind_without_any_hit_to_open() {
    var provider = new FakeProvider().finds(
        hit(PERSON, "Person P"),
        hit(CUSTOMER, "MUSTERKUNDE", open("MUSTERKUNDE")));

    assertThat(service(provider).search("muster")).extracting(PaletteGroup::kind).containsExactly(CUSTOMER);
  }

  // --- a provider that is denied ------------------------------------------------------------------

  @Test
  void leaves_out_a_provider_denied_in_its_search_and_still_asks_the_others() {
    var denied = new FakeProvider()
        .finds(hit(CUSTOMER, "MUSTERKUNDE", open("MUSTERKUNDE")))
        .failsToSearch(new AuthorizationException(AA_NOT_ATHORIZED));
    var orders = new FakeProvider().finds(hit(CUSTOMERORDER, "MUSTER-01", open("MUSTER-01")));

    var groups = service(denied, orders).search("muster");

    assertThat(groups).extracting(PaletteGroup::kind).containsExactly(CUSTOMERORDER);
    assertThat(titles(groups, CUSTOMERORDER)).containsExactly("MUSTER-01");
  }

  /** Its own hits stay: being denied further targets says nothing about what it found itself. */
  @Test
  void leaves_out_a_provider_denied_in_its_targets_and_still_adds_those_of_the_others() {
    var denied = new FakeProvider()
        .finds(hit(CUSTOMER, "MUSTERKUNDE", open("MUSTERKUNDE")))
        .adds(CUSTOMERORDER, "MUSTER-01", target("/denied", 1))
        .failsToAddTargets(new AuthorizationException(AA_NOT_ATHORIZED));
    var orders = new FakeProvider().finds(hit(CUSTOMERORDER, "MUSTER-01", target("/open", PaletteTarget.OPEN)));
    var budget = new FakeProvider().adds(CUSTOMERORDER, "MUSTER-01", target("/controlling", 2));

    var groups = service(denied, orders, budget).search("muster");

    assertThat(hrefs(onlyHit(groups, CUSTOMERORDER))).containsExactly("/open", "/controlling");
    assertThat(titles(groups, CUSTOMER)).containsExactly("MUSTERKUNDE");
  }

  /** Only a denial is caught; a defect must not look like "nothing found". */
  @Test
  void passes_on_a_failure_that_is_not_a_denial() {
    var broken = new FakeProvider().failsToSearch(new IllegalStateException("defect"));
    var orders = new FakeProvider().finds(hit(CUSTOMERORDER, "MUSTER-01", open("MUSTER-01")));

    assertThatThrownBy(() -> service(broken, orders).search("muster")).isInstanceOf(IllegalStateException.class);
  }

  // --- groups -------------------------------------------------------------------------------------

  @Test
  void groups_the_hits_in_the_order_of_the_kinds_and_leaves_out_kinds_without_hits() {
    var people = new FakeProvider().finds(hit(PERSON, "Person P", open("ppp")));
    var customers = new FakeProvider().finds(hit(CUSTOMER, "MUSTERKUNDE", open("MUSTERKUNDE")));
    var orders = new FakeProvider().finds(hit(CUSTOMERORDER, "MUSTER-01", open("MUSTER-01")));

    assertThat(service(people, customers, orders).search("muster")).extracting(PaletteGroup::kind)
        .containsExactly(CUSTOMERORDER, CUSTOMER, PERSON);
  }

  // --- the parameters of the commands (#1158) -----------------------------------------------------

  @Test
  void hands_every_provider_the_request_and_keeps_their_order() {
    var requests = new ArrayList<PaletteSuggestionRequest>();
    PaletteProvider first = new PaletteProvider() { };
    PaletteProvider months = new PaletteProvider() {
      @Override
      public List<PaletteSuggestion> suggest(PaletteSuggestionRequest request) {
        requests.add(request);
        return List.of(PaletteSuggestion.of("2026-09", "2026-09", null, null), PaletteSuggestion.of("2026-08", "2026-08", null, null));
      }
    };
    PaletteProvider more = new PaletteProvider() {
      @Override
      public List<PaletteSuggestion> suggest(PaletteSuggestionRequest request) {
        return List.of(PaletteSuggestion.of("2026-07", "2026-07", null, null));
      }
    };

    var suggestions = service(first, months, more).suggest(PaletteCommand.ACCEPT, PaletteParameter.MONTH, "  SEP ",
        LocalDate.of(2026, 9, 25), 7L);

    assertThat(suggestions).extracting(PaletteSuggestion::value).containsExactly("2026-09", "2026-08", "2026-07");
    assertThat(requests).singleElement().satisfies(request -> {
      assertThat(request.command()).isEqualTo(PaletteCommand.ACCEPT);
      assertThat(request.parameter()).isEqualTo(PaletteParameter.MONTH);
      assertThat(request.query().text()).isEqualTo("sep");
      assertThat(request.date()).isEqualTo(LocalDate.of(2026, 9, 25));
      assertThat(request.contractId()).isEqualTo(7L);
    });
  }

  /** Unlike the search, an empty query is asked: the providers then offer what they would put first. */
  @Test
  void asks_the_providers_with_an_empty_query_as_well() {
    PaletteProvider months = new PaletteProvider() {
      @Override
      public List<PaletteSuggestion> suggest(PaletteSuggestionRequest request) {
        return List.of(PaletteSuggestion.of("2026-09", "2026-09", null, null));
      }
    };

    assertThat(service(months).suggest(PaletteCommand.RELEASE, PaletteParameter.MONTH, "", null, null)).hasSize(1);
  }

  @Test
  void offers_at_most_the_limit_of_values() {
    PaletteProvider many = new PaletteProvider() {
      @Override
      public List<PaletteSuggestion> suggest(PaletteSuggestionRequest request) {
        return IntStream.range(0, 20).mapToObj(i -> PaletteSuggestion.of("v" + i, "v" + i, null, null)).toList();
      }
    };

    assertThat(service(many).suggest(PaletteCommand.BOOK, PaletteParameter.SUBORDER, "v", null, null))
        .hasSize(PaletteSearchService.SUGGESTIONS);
  }

  @Test
  void a_denied_provider_offers_no_values_and_takes_nobody_elses_along() {
    PaletteProvider denied = new PaletteProvider() {
      @Override
      public List<PaletteSuggestion> suggest(PaletteSuggestionRequest request) {
        throw new AuthorizationException(AA_NOT_ATHORIZED);
      }
    };
    PaletteProvider orders = new PaletteProvider() {
      @Override
      public List<PaletteSuggestion> suggest(PaletteSuggestionRequest request) {
        return List.of(PaletteSuggestion.of("MUSTER-01", "MUSTER-01", null, null));
      }
    };

    assertThat(service(denied, orders).suggest(PaletteCommand.CONTROLLING, PaletteParameter.CUSTOMERORDER, "mu", null, null))
        .extracting(PaletteSuggestion::value).containsExactly("MUSTER-01");
  }

  // --- the transaction ----------------------------------------------------------------------------

  /**
   * Nothing is written, and a commit would fail where an exception caught within the search crossed
   * a service; the search ends its transaction with a rollback, and with no exception.
   */
  @Test
  void runs_in_one_read_only_transaction_that_is_rolled_back() {
    var transactions = new RecordingTransactionManager();
    var provider = new FakeProvider().finds(hit(CUSTOMERORDER, "MUSTER-01", open("MUSTER-01")));

    assertThat(new PaletteSearchService(List.of(provider), transactions).search("muster")).hasSize(1);
    assertThat(transactions.ends).containsExactly("read-only rollback");
  }

  // --- helpers ------------------------------------------------------------------------------------

  private static PaletteSearchService service(PaletteProvider... providers) {
    return new PaletteSearchService(List.of(providers), new RecordingTransactionManager());
  }

  /** How the search ends its transactions; the real one is {@code PaletteSearchIntegrationTest}'s. */
  private static class RecordingTransactionManager extends AbstractPlatformTransactionManager {

    private final List<String> ends = new ArrayList<>();

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      ends.add((status.isReadOnly() ? "read-only " : "") + "commit");
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      ends.add((status.isReadOnly() ? "read-only " : "") + "rollback");
    }
  }

  /** A current hit matching at the title start, with the key as its title. */
  private static PaletteHit hit(PaletteKind kind, String key, PaletteTarget... targets) {
    return new PaletteHit(kind, key, key, null, null, false, false, 4, List.of(targets));
  }

  private static PaletteHit ranked(String title, boolean ended, boolean hidden, int match) {
    return new PaletteHit(CUSTOMERORDER, title, title, null, null, ended, hidden, match, List.of(open(title)));
  }

  private static PaletteTarget open(String key) {
    return target("/open/" + key, PaletteTarget.OPEN);
  }

  private static PaletteTarget target(String href, int rank) {
    return new PaletteTarget(PaletteText.of("main.palette.target.test"), href, rank);
  }

  /** MUSTER-01, MUSTER-02, …: ranked in this order by their titles alone. */
  private static List<String> signs(int count) {
    return IntStream.rangeClosed(1, count).mapToObj(n -> "MUSTER-%02d".formatted(n)).toList();
  }

  private static List<PaletteHit> hits(List<PaletteGroup> groups, PaletteKind kind) {
    return groups.stream().filter(group -> group.kind() == kind).findFirst()
        .map(PaletteGroup::hits).orElse(List.of());
  }

  private static List<String> titles(List<PaletteGroup> groups, PaletteKind kind) {
    return hits(groups, kind).stream().map(PaletteHit::title).toList();
  }

  private static PaletteHit onlyHit(List<PaletteGroup> groups, PaletteKind kind) {
    var hits = hits(groups, kind);
    assertThat(hits).hasSize(1);
    return hits.getFirst();
  }

  private static List<String> hrefs(PaletteHit hit) {
    return hit.targets().stream().map(PaletteTarget::href).toList();
  }

  /** Finds and adds what it is told to, fails where it is told to, and remembers what it was asked. */
  private static final class FakeProvider implements PaletteProvider {

    private final List<PaletteHit> found = new ArrayList<>();
    private final Map<PaletteKind, Map<String, List<PaletteTarget>>> added = new EnumMap<>(PaletteKind.class);
    private final List<PaletteQuery> queries = new ArrayList<>();
    private final Map<PaletteKind, List<PaletteHit>> offered = new EnumMap<>(PaletteKind.class);
    private RuntimeException searchFailure;
    private RuntimeException targetsFailure;

    FakeProvider finds(PaletteHit... hits) {
      return finds(List.of(hits));
    }

    FakeProvider finds(List<PaletteHit> hits) {
      found.addAll(hits);
      return this;
    }

    FakeProvider adds(PaletteKind kind, String key, PaletteTarget... targets) {
      added.computeIfAbsent(kind, k -> new HashMap<>()).put(key, List.of(targets));
      return this;
    }

    FakeProvider failsToSearch(RuntimeException failure) {
      searchFailure = failure;
      return this;
    }

    FakeProvider failsToAddTargets(RuntimeException failure) {
      targetsFailure = failure;
      return this;
    }

    @Override
    public List<PaletteHit> search(PaletteQuery query) {
      queries.add(query);
      if (searchFailure != null) {
        throw searchFailure;
      }
      return List.copyOf(found);
    }

    @Override
    public Map<String, List<PaletteTarget>> targetsFor(PaletteKind kind, List<PaletteHit> hits) {
      offered.put(kind, hits);
      if (targetsFailure != null) {
        throw targetsFailure;
      }
      return added.getOrDefault(kind, Map.of());
    }
  }
}
