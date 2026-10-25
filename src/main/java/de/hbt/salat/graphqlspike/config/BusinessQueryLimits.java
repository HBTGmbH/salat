package de.hbt.salat.graphqlspike.config;

import graphql.ExecutionResult;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.language.Field;

/**
 * Depth and complexity limits for business queries only (concept 6.2). The introspection query of GraphiQL and of
 * most tools has a depth of 15, so a limit of 8 would lock out exactly the callers the concept wants to describe the
 * schema to themselves (6.1). An operation asking nothing but {@code __schema}, {@code __type} or
 * {@code __typename} is left to graphql-java's {@code GoodFaithIntrospection}, which bounds introspection by its own
 * field and depth counts and is on by default.
 */
final class BusinessQueryLimits {

  private BusinessQueryLimits() {
  }

  static boolean isIntrospectionOnly(ExecutionContext context) {
    return context.getOperationDefinition().getSelectionSet().getSelections().stream()
        .allMatch(selection -> selection instanceof Field field && field.getName().startsWith("__"));
  }

  static final class Depth extends MaxQueryDepthInstrumentation {

    Depth(int maxDepth) {
      super(maxDepth);
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecuteOperation(
        InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {
      return isIntrospectionOnly(parameters.getExecutionContext())
          ? SimpleInstrumentationContext.noOp()
          : super.beginExecuteOperation(parameters, state);
    }
  }

  static final class Complexity extends MaxQueryComplexityInstrumentation {

    Complexity(int maxComplexity) {
      super(maxComplexity);
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecuteOperation(
        InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {
      return isIntrospectionOnly(parameters.getExecutionContext())
          ? SimpleInstrumentationContext.noOp()
          : super.beginExecuteOperation(parameters, state);
    }
  }
}
