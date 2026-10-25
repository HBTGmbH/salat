package de.hbt.salat.common.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import graphql.language.Description;
import graphql.language.EnumTypeDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.diff.SchemaDiff;
import graphql.schema.diff.SchemaDiffSet;
import graphql.schema.diff.reporting.CapturingReporter;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.SchemaPrinter;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.UnExecutableSchemaGenerator;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * The schema is the documentation of the GraphQL interface and a contract with callers nobody knows (#1532,
 * concept 4): every type, field, argument and enum value says what it means, and the schema grows only by adding.
 *
 * <p>{@code docs/graphql-schema.graphqls} is the published schema. A change that breaks it — a field removed or
 * renamed, a type narrowed, an argument made mandatory — fails here; what is to be retired gets
 * {@code @deprecated} instead. A change that adds is written to the file with
 * {@code -Dgraphql.publishSchema=true} and comes into review with the code.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class GraphqlSchemaContractTest {

  private static final Path PUBLISHED = Path.of("docs/graphql-schema.graphqls");

  @Test
  void every_type_field_argument_and_value_is_described() throws IOException {
    TypeDefinitionRegistry registry = registry();
    List<String> undescribed = new ArrayList<>();

    registry.types().values().forEach(type -> {
      switch (type) {
        case ObjectTypeDefinition object -> {
          check(object.getDescription(), "type " + object.getName(), undescribed);
          fields(object.getName(), object.getFieldDefinitions(), undescribed);
        }
        case InputObjectTypeDefinition input -> {
          check(input.getDescription(), "input " + input.getName(), undescribed);
          values(input.getName(), input.getInputValueDefinitions(), undescribed);
        }
        case EnumTypeDefinition enumType -> {
          check(enumType.getDescription(), "enum " + enumType.getName(), undescribed);
          enumType.getEnumValueDefinitions().forEach(value ->
              check(value.getDescription(), enumType.getName() + "." + value.getName(), undescribed));
        }
        default -> undescribed.add("unexpected kind of type " + type.getName());
      }
    });
    registry.objectTypeExtensions().values().stream().flatMap(List::stream)
        .forEach(extension -> fields(extension.getName(), extension.getFieldDefinitions(), undescribed));
    registry.scalars().values().stream()
        .filter(scalar -> scalar.getSourceLocation() != null)
        .forEach(scalar -> check(scalar.getDescription(), "scalar " + scalar.getName(), undescribed));

    assertThat(undescribed).as("described in German, see docs/guides/backend-patterns.md, GraphQL").isEmpty();
  }

  @Test
  void the_schema_does_not_break_the_published_one() throws IOException {
    var reporter = new CapturingReporter();

    new SchemaDiff().diffSchema(SchemaDiffSet.diffSetFromSdl(Files.readString(PUBLISHED), current()), reporter);

    assertThat(reporter.getBreakages())
        .as("breaking changes against %s; retire a field with @deprecated instead", PUBLISHED)
        .isEmpty();
  }

  @Test
  void the_published_schema_is_the_current_one() throws IOException {
    String current = current();
    if (Boolean.getBoolean("graphql.publishSchema")) {
      Files.writeString(PUBLISHED, current);
    }

    assertThat(Files.readString(PUBLISHED))
        .as("the schema changed; publish it with -Dgraphql.publishSchema=true and review the difference")
        .isEqualTo(current);
  }

  private static String current() throws IOException {
    var schema = UnExecutableSchemaGenerator.makeUnExecutableSchema(registry());
    return new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectives(false)).print(schema);
  }

  private static TypeDefinitionRegistry registry() throws IOException {
    var registry = new TypeDefinitionRegistry();
    var parser = new SchemaParser();
    for (Resource file : new PathMatchingResourcePatternResolver().getResources("classpath:graphql/*.graphqls")) {
      try (var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
        registry.merge(parser.parse(reader));
      }
    }
    return registry;
  }

  private static void fields(String type, List<FieldDefinition> fields, List<String> undescribed) {
    fields.forEach(field -> {
      check(field.getDescription(), type + "." + field.getName(), undescribed);
      values(type + "." + field.getName(), field.getInputValueDefinitions(), undescribed);
    });
  }

  private static void values(String owner, List<InputValueDefinition> values, List<String> undescribed) {
    values.forEach(value -> check(value.getDescription(), owner + "(" + value.getName() + ")", undescribed));
  }

  private static void check(Description description, String what, List<String> undescribed) {
    if (description == null || description.getContent().isBlank()) {
      undescribed.add(what);
    }
  }
}
