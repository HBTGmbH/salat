package de.hbt.salat.common.graphql;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.function.Function;

/**
 * {@code Date}, {@code Time} and {@code Duration} as ISO-8601 strings (concept 4). Written by hand: the extended scalars of
 * graphql-java are a further dependency and bring no {@code java.time.Duration}.
 */
final class GraphqlScalars {

  static final GraphQLScalarType DATE = scalar("Date", LocalDate.class, LocalDate::parse);
  static final GraphQLScalarType DURATION = scalar("Duration", Duration.class, Duration::parse);
  static final GraphQLScalarType TIME = scalar("Time", LocalTime.class, LocalTime::parse);

  private GraphqlScalars() {
  }

  private static <T> GraphQLScalarType scalar(String name, Class<T> type, Function<String, T> parse) {
    return GraphQLScalarType.newScalar()
        .name(name)
        .coercing(new Coercing<T, String>() {
          @Override
          public String serialize(Object value, GraphQLContext context, Locale locale) {
            if (type.isInstance(value)) {
              return value.toString();
            }
            throw new CoercingSerializeException("Expected " + name + " but got " + value);
          }

          @Override
          public T parseValue(Object input, GraphQLContext context, Locale locale) {
            if (input instanceof String text) {
              try {
                return parse.apply(text);
              } catch (DateTimeParseException ex) {
                throw new CoercingParseValueException("Not an ISO-8601 " + name + ": " + text, ex);
              }
            }
            throw new CoercingParseValueException("Expected a string for " + name);
          }

          @Override
          public T parseLiteral(Value<?> input, CoercedVariables variables, GraphQLContext context, Locale locale) {
            if (input instanceof StringValue text) {
              try {
                return parse.apply(text.getValue());
              } catch (DateTimeParseException ex) {
                throw new CoercingParseLiteralException("Not an ISO-8601 " + name + ": " + text.getValue(), ex);
              }
            }
            throw new CoercingParseLiteralException("Expected a string for " + name);
          }
        })
        .build();
  }
}
