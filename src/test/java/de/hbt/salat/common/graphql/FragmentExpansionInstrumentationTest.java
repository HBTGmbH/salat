package de.hbt.salat.common.graphql;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import graphql.ExecutionInput;
import graphql.execution.AbortExecutionException;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.introspection.IntrospectionQuery;
import graphql.language.Document;
import graphql.parser.Parser;
import java.time.Duration;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * The guard against fragment-spread amplification (concept 6.2). A ladder of fragments that each spread the next two
 * expands to 2^n collected fields from a tiny document; good-faith introspection and the depth/complexity limits do
 * not catch it, so this one must — and it must leave the real introspection query and ordinary queries alone.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class FragmentExpansionInstrumentationTest {

  private final FragmentExpansionInstrumentation instrumentation = new FragmentExpansionInstrumentation();

  @Test
  void a_fragment_ladder_on_an_introspection_type_is_refused() {
    assertThatThrownBy(() -> check(ladder("__Type", "name", 25)))
        .isInstanceOf(AbortExecutionException.class)
        .hasMessageContaining("too many selections");
  }

  @Test
  void a_fragment_ladder_on_a_business_type_is_refused() {
    assertThatThrownBy(() -> check("{ me { ...F0a ...F0b } } " + ladderFragments("Me", "sign", 25)))
        .isInstanceOf(AbortExecutionException.class)
        .hasMessageContaining("too many selections");
  }

  /** The DP is linear in the fragment graph and saturates: an absurd depth is refused at once, not expanded. */
  @Test
  void an_absurd_ladder_is_refused_without_expanding_it() {
    assertTimeoutPreemptively(Duration.ofSeconds(2),
        () -> assertThatThrownBy(() -> check(ladder("__Type", "name", 60)))
            .isInstanceOf(AbortExecutionException.class));
  }

  /** The query GraphiQL and the tools actually send must pass — its fragments do not amplify. */
  @Test
  void the_standard_introspection_query_passes() {
    assertThatCode(() -> check(IntrospectionQuery.INTROSPECTION_QUERY)).doesNotThrowAnyException();
  }

  @Test
  void an_ordinary_business_query_passes() {
    assertThatCode(() -> check("{ me { employeeId sign bookableSuborders(on: \"2026-10-26\") { suborderId } } }"))
        .doesNotThrowAnyException();
  }

  /** A document just under the limit still passes — the guard refuses amplification, not size as such. */
  @Test
  void a_fragment_spread_a_few_thousand_times_without_nesting_passes() {
    var operation = new StringBuilder("{ me {");
    for (int i = 0; i < 5000; i++) {
      operation.append(" ...F");
    }
    operation.append(" } } fragment F on Me { sign }");
    assertThatCode(() -> check(operation.toString())).doesNotThrowAnyException();
  }

  private void check(String query) {
    Document document = Parser.parse(query);
    instrumentation.beginValidation(
        new InstrumentationValidationParameters(ExecutionInput.newExecutionInput(query).build(), document, null), null);
  }

  /** The operation plus the fragments of a ladder of the given depth. */
  private static String ladder(String type, String leaf, int depth) {
    return "{ __type(name: \"Query\") { ...F0a ...F0b } } " + ladderFragments(type, leaf, depth);
  }

  /** Fragments F0a/F0b … each spreading the next pair, down to the leaf field — 2^depth selections after expansion. */
  private static String ladderFragments(String type, String leaf, int depth) {
    var sb = new StringBuilder();
    for (int i = 0; i < depth; i++) {
      for (String s : new String[] {"a", "b"}) {
        sb.append("fragment F").append(i).append(s).append(" on ").append(type)
            .append(" { ...F").append(i + 1).append("a ...F").append(i + 1).append("b } ");
      }
    }
    sb.append("fragment F").append(depth).append("a on ").append(type).append(" { ").append(leaf).append(" } ");
    sb.append("fragment F").append(depth).append("b on ").append(type).append(" { ").append(leaf).append(" } ");
    return sb.toString();
  }
}
