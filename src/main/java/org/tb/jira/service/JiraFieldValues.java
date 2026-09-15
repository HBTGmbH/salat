package org.tb.jira.service;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Turns the shapes a JIRA field value arrives in into one string (#881).
 *
 * <p>The REST API answers a field with whatever its type is: a scalar for text, numbers and dates,
 * an object for a single select ({@code {"value": …}}), a status, priority or resolution
 * ({@code {"name": …}}), an issue or project reference ({@code {"key": …}}), a user
 * ({@code {"displayName": …}} — on Cloud that is all a user carries), and a list of any of those for
 * a multi select, for {@code components} or for {@code fixVersions}. Reading such a value with
 * {@code String.valueOf} yields the {@code toString} of a {@code Map} — technically a value, but not
 * one anything can evaluate.
 *
 * <p>Nothing here is specific to custom fields: a standard field is configured by its response key
 * exactly like a custom one and goes through the same shapes.
 */
final class JiraFieldValues {

  /** Separator of a multi-valued field, the same one the replication uses for {@code labels}. */
  private static final String SEPARATOR = ",";

  /**
   * Bounds one stored value. These fields carry classifications — a category, a status, a
   * reference — not prose; a wrongly configured long text field must not blow up every row of the
   * table.
   */
  private static final int MAX_LENGTH = 4000;

  private JiraFieldValues() {
  }

  /**
   * The value the configured entry addresses, or {@code null} when it carries nothing usable.
   *
   * <p>{@code path} is the field id, optionally followed by a dotted path into the value —
   * {@code customfield_10200.child.value} reads the second level of a cascading select,
   * {@code assignee.emailAddress} a property other than the one the default order picks. A step over
   * a list applies to every element, so {@code components.name} answers all component names.
   */
  static String toValue(Map<String, Object> fields, String path) {
    if (fields == null || path == null || path.isBlank()) {
      return null;
    }
    var segments = path.split("\\.");
    Object current = fields.get(segments[0]);
    for (int i = 1; i < segments.length && current != null; i++) {
      current = step(current, segments[i]);
    }
    return toValue(current);
  }

  /** One step of a path. A scalar has no parts, so a path that runs past the value ends nowhere. */
  private static Object step(Object value, String segment) {
    return switch (value) {
      case null -> null;
      case Map<?, ?> map -> map.get(segment);
      case Collection<?> values -> values.stream()
          .map(element -> step(element, segment))
          .filter(Objects::nonNull)
          .toList();
      default -> null;
    };
  }

  /** The value as a string, or {@code null} when the field carries nothing usable. */
  static String toValue(Object raw) {
    var value = convert(raw);
    if (value == null || value.isBlank()) {
      return null;
    }
    value = value.trim();
    return value.length() <= MAX_LENGTH ? value : value.substring(0, MAX_LENGTH);
  }

  private static String convert(Object raw) {
    return switch (raw) {
      case null -> null;
      case String s -> s;
      case Map<?, ?> map -> fromObject(map);
      case Collection<?> values -> values.stream()
          .map(JiraFieldValues::convert)
          .filter(v -> v != null && !v.isBlank())
          .map(String::trim)
          .collect(Collectors.joining(SEPARATOR));
      default -> String.valueOf(raw);
    };
  }

  /**
   * The one property of an object value that carries the value itself. The order follows how
   * specific the property is: {@code value} belongs to a select option and is the value proper,
   * {@code name} to a status, priority, component or version, {@code key} to an issue or project
   * reference, {@code displayName} to a user — which on Cloud no longer has a {@code name} at all.
   */
  private static String fromObject(Map<?, ?> map) {
    for (var property : new String[]{"value", "name", "key", "displayName"}) {
      var candidate = convert(map.get(property));
      if (candidate != null && !candidate.isBlank()) {
        return candidate;
      }
    }
    return null;
  }
}
