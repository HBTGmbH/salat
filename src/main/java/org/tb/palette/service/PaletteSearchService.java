package org.tb.palette.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.tb.auth.domain.Authorized;
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

/**
 * The object search of the command palette (#1157, ADR-0031): asks every module's
 * {@link PaletteProvider} and puts their answers together.
 *
 * <ol>
 *   <li>Every provider searches; hits of the same kind and key are one object, their targets are
 *       merged.</li>
 *   <li>Per kind the hits are ranked — current before ended and hidden ones (ADR-0012, ADR-0029),
 *       then by how well they match — and twice as many as are shown go on.</li>
 *   <li>Every provider may add targets to those.</li>
 *   <li>A hit without any target is dropped: nothing is offered that could not be opened. Of the
 *       rest, {@link PaletteQuery#HITS_PER_KIND} per kind are shown.</li>
 * </ol>
 *
 * <p>What a user may see is each provider's decision, not this service's. A provider whose decision
 * ends in an {@link AuthorizationException} contributes nothing — the exception would otherwise
 * answer the whole search with 403 and take every other provider's hits along.
 *
 * <p>The search runs in one read-only transaction, which is <b>always rolled back</b>. An exception
 * leaving a service method marks the surrounding transaction for rollback even when it is caught
 * afterwards — a caught {@link AuthorizationException} as much as one a provider catches itself —
 * and a commit would then answer the whole search with an {@code UnexpectedRollbackException}.
 * There is nothing to commit, so the transaction is marked for rollback from the start, and the
 * rollback is the expected one. Without a transaction of its own the search would be safe as well,
 * but every service call would open one: measured, 10 to 24 ms more per search (PR to #1157).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Authorized
public class PaletteSearchService {

  static final Comparator<PaletteHit> RANKING = Comparator.comparing(PaletteHit::hidden)
      .thenComparing(PaletteHit::ended)
      .thenComparing(Comparator.comparingInt(PaletteHit::match).reversed())
      .thenComparing(PaletteHit::title, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

  /** How many values the palette offers for a parameter. */
  static final int SUGGESTIONS = 8;

  private static final Comparator<PaletteTarget> TARGET_ORDER = Comparator.comparingInt(PaletteTarget::rank);

  private final List<PaletteProvider> providers;
  private final PlatformTransactionManager transactionManager;

  public List<PaletteGroup> search(String text) {
    var query = PaletteQuery.of(text);
    if (!query.isSearchable()) {
      return List.of();
    }
    return readOnly(() -> collect(query));
  }

  /**
   * The values for a parameter of a command (#1158): the answers of every provider in the order of
   * the providers, each provider's own ranking kept, at most {@link #SUGGESTIONS} of them. Unlike the
   * search, an empty query is asked as well — then the providers offer what they would put first.
   * The same transaction as for the search, for the same reason.
   */
  public List<PaletteSuggestion> suggest(PaletteCommand command, PaletteParameter parameter, String text,
      LocalDate date, Long contractId) {
    var request = new PaletteSuggestionRequest(command, parameter, PaletteQuery.of(text), date, contractId);
    return readOnly(() -> providers.stream()
        .flatMap(provider -> safely(provider, () -> provider.suggest(request), List.<PaletteSuggestion>of()).stream())
        .limit(SUGGESTIONS)
        .toList());
  }

  private <T> T readOnly(Supplier<T> work) {
    var transaction = new TransactionTemplate(transactionManager);
    transaction.setReadOnly(true);
    return transaction.execute(status -> {
      status.setRollbackOnly();
      return work.get();
    });
  }

  private List<PaletteGroup> collect(PaletteQuery query) {
    var found = new EnumMap<PaletteKind, Map<String, PaletteHit>>(PaletteKind.class);
    for (var provider : providers) {
      for (var hit : safely(provider, () -> provider.search(query), List.<PaletteHit>of())) {
        found.computeIfAbsent(hit.kind(), kind -> new LinkedHashMap<>())
            .merge(hit.key(), hit, (kept, more) -> kept.withTargets(concat(kept.targets(), more.targets())));
      }
    }

    var groups = new ArrayList<PaletteGroup>();
    for (var kind : PaletteKind.values()) {
      var candidates = found.getOrDefault(kind, Map.of()).values().stream()
          .sorted(RANKING)
          .limit(2L * PaletteQuery.HITS_PER_KIND)
          .toList();
      if (candidates.isEmpty()) {
        continue;
      }
      var added = new HashMap<String, List<PaletteTarget>>();
      for (var provider : providers) {
        safely(provider, () -> provider.targetsFor(kind, candidates), Map.<String, List<PaletteTarget>>of())
            .forEach((key, targets) -> added.computeIfAbsent(key, k -> new ArrayList<>()).addAll(targets));
      }
      var hits = candidates.stream()
          .map(hit -> hit.withTargets(concat(hit.targets(), added.getOrDefault(hit.key(), List.of()))
              .stream().sorted(TARGET_ORDER).toList()))
          .filter(hit -> !hit.targets().isEmpty())
          .limit(PaletteQuery.HITS_PER_KIND)
          .toList();
      if (!hits.isEmpty()) {
        groups.add(new PaletteGroup(kind, hits));
      }
    }
    return groups;
  }

  private static <T> T safely(PaletteProvider provider, Supplier<T> contribution, T nothing) {
    try {
      return contribution.get();
    } catch (AuthorizationException e) {
      log.debug("{} contributes nothing to the palette: {}", provider.getClass().getSimpleName(), e.getMessage());
      return nothing;
    }
  }

  private static List<PaletteTarget> concat(List<PaletteTarget> first, List<PaletteTarget> second) {
    return Stream.concat(first.stream(), second.stream()).toList();
  }
}
