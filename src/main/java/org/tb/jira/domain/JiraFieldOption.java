package org.tb.jira.domain;

/**
 * One entry the field picker offers (#1013).
 *
 * @param key the response key to write into the configured list — the field id, or the id plus a
 *     path when the option addresses a part of the value
 * @param name the display name from JIRA
 * @param type the value shape in JIRA's own words ({@code cascadingselect}, {@code select},
 *     {@code string}, …). Not translated on purpose: it is the vocabulary of the foreign system, and
 *     a translation table for it would be out of date the moment an instance adds a field type.
 * @param secondLevel whether this option addresses the second level of a cascading select rather
 *     than the field itself — the one case where a path is needed and where the catalogue can say so
 */
public record JiraFieldOption(String key, String name, String type, boolean secondLevel) {

  public static JiraFieldOption of(String key, String name, String type) {
    return new JiraFieldOption(key, name, type, false);
  }

  public static JiraFieldOption secondLevelOf(String key, String name, String type) {
    return new JiraFieldOption(key, name, type, true);
  }
}
