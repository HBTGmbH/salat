package de.hbt.salat.common.graphql;

import graphql.execution.AbortExecutionException;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.validation.ValidationError;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Refuses a document whose fragment spreads expand to more than {@link #MAX_EXPANDED_SELECTIONS} selections, before
 * anything normalizes it (concept 6.2).
 *
 * <p>A ladder of fragments that each spread the next two expands to 2^n collected fields from a document of a few
 * hundred bytes. graphql-java's good-faith introspection guard does not stop it: the fields merge, so its field-count
 * (500) and depth (20) limits both pass, while the work to collect the 2^n fields before they merge is exponential.
 * The depth and complexity instrumentation ({@link BusinessQueryLimits}) does not stop it either — it is skipped for
 * introspection-only queries and runs later, inside execution.
 *
 * <p>The count is worked out from the parsed document in {@code beginValidation} — before validation and before the
 * expensive normalization — in time linear in the fragment graph: the expanded size of a fragment is computed once and
 * memoised, the running sum saturates at the limit, so a ladder is refused without ever expanding it. The message is a
 * plain string like the sibling graphql-java limits, not a {@code GQ-…} code: this guard sits at the graphql-java
 * layer, before the resolvers and {@code GraphqlExceptionResolver} the business codes run through.
 */
final class FragmentExpansionInstrumentation extends SimplePerformantInstrumentation {

  /**
   * Far above any legitimate document — the standard introspection query of GraphiQL expands to a few thousand — and
   * far below a ladder, which crosses it at depth 17 (2^17), well under a second.
   */
  static final long MAX_EXPANDED_SELECTIONS = 100_000;

  @Override
  public InstrumentationContext<List<ValidationError>> beginValidation(
      InstrumentationValidationParameters parameters, InstrumentationState state) {
    Document document = parameters.getDocument();
    Map<String, FragmentDefinition> fragments = new HashMap<>();
    for (FragmentDefinition fragment : document.getDefinitionsOfType(FragmentDefinition.class)) {
      fragments.put(fragment.getName(), fragment);
    }
    var memo = new HashMap<String, Long>();
    long total = 0;
    for (OperationDefinition operation : document.getDefinitionsOfType(OperationDefinition.class)) {
      total = saturatingAdd(total, expandedSize(operation.getSelectionSet(), fragments, memo, new HashSet<>()));
      if (total > MAX_EXPANDED_SELECTIONS) {
        throw new AbortExecutionException(
            "The query expands to too many selections (more than " + MAX_EXPANDED_SELECTIONS + ")");
      }
    }
    return super.beginValidation(parameters, state);
  }

  /**
   * How many selections {@code set} expands to, a fragment spread counting as the expanded size of its fragment.
   * Memoised per fragment (the size does not depend on where the fragment is spread) and saturated at
   * {@link #MAX_EXPANDED_SELECTIONS}, so the sum never overflows and the walk stops once the limit is passed. A spread
   * back onto a fragment already on the current path is a cycle — invalid, and refused by validation later — and is
   * skipped here only to keep the walk finite.
   */
  private long expandedSize(SelectionSet set, Map<String, FragmentDefinition> fragments,
      Map<String, Long> memo, Set<String> onPath) {
    if (set == null) {
      return 0;
    }
    long size = 0;
    for (Selection<?> selection : set.getSelections()) {
      if (selection instanceof Field field) {
        size = saturatingAdd(size, 1 + expandedSize(field.getSelectionSet(), fragments, memo, onPath));
      } else if (selection instanceof InlineFragment inline) {
        size = saturatingAdd(size, expandedSize(inline.getSelectionSet(), fragments, memo, onPath));
      } else if (selection instanceof FragmentSpread spread) {
        size = saturatingAdd(size, fragmentSize(spread.getName(), fragments, memo, onPath));
      }
      if (size > MAX_EXPANDED_SELECTIONS) {
        return MAX_EXPANDED_SELECTIONS + 1;
      }
    }
    return size;
  }

  private long fragmentSize(String name, Map<String, FragmentDefinition> fragments,
      Map<String, Long> memo, Set<String> onPath) {
    FragmentDefinition fragment = fragments.get(name);
    if (fragment == null || !onPath.add(name)) {
      return 0; // unknown fragment (refused by validation) or a cycle (refused by validation) — do not recurse
    }
    Long cached = memo.get(name);
    long size = cached != null ? cached : expandedSize(fragment.getSelectionSet(), fragments, memo, onPath);
    memo.put(name, size);
    onPath.remove(name);
    return size;
  }

  private static long saturatingAdd(long a, long b) {
    long sum = a + b;
    return sum < a ? Long.MAX_VALUE : sum; // a and b are never negative
  }
}
