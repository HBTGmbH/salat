package de.hbt.salat.common.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.introspection.IntrospectionQuery;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * The limits of the GraphQL interface (#1532) on a schema that nests as deep as one likes — the own schema does not
 * reach depth 8 with business fields.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class BusinessQueryLimitsTest {

  private final GraphQL graphQL = GraphQL.newGraphQL(new SchemaGenerator().makeExecutableSchema(
          new SchemaParser().parse("type Query { node: Node } type Node { child: Node, name: String }"),
          RuntimeWiring.newRuntimeWiring().build()))
      .instrumentation(new ChainedInstrumentation(List.of(
          new BusinessQueryLimits.Depth(GraphqlConfiguration.MAX_DEPTH),
          new BusinessQueryLimits.Complexity(GraphqlConfiguration.MAX_COMPLEXITY))))
      .build();

  @Test
  void a_business_query_within_the_depth_runs() {
    ExecutionResult result = graphQL.execute("{ node { child { child { child { name } } } } }");

    assertThat(result.getErrors()).isEmpty();
  }

  @Test
  void a_business_query_deeper_than_the_limit_is_refused() {
    ExecutionResult result = graphQL.execute(
        "{ node { child { child { child { child { child { child { child { child { name } } } } } } } } } }");

    assertThat(result.getErrors()).singleElement()
        .satisfies(error -> assertThat(error.getMessage()).contains("depth"));
  }

  /** The query GraphiQL sends, with a depth far above 8. */
  @Test
  void the_introspection_query_of_the_tools_is_exempt() {
    ExecutionResult result = graphQL.execute(IntrospectionQuery.INTROSPECTION_QUERY);

    assertThat(result.getErrors()).isEmpty();
  }

  @Test
  void a_business_field_next_to_introspection_keeps_the_limit() {
    ExecutionResult result = graphQL.execute(
        "{ __typename node { child { child { child { child { child { child { child { child { name } } } } } } } } } }");

    assertThat(result.getErrors()).isNotEmpty();
  }
}
