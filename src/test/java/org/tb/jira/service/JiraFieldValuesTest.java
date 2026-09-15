package org.tb.jira.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * The shapes a JIRA field value arrives in (#881). Custom and standard fields alike — the response
 * key decides what is read, not what kind of field it belongs to.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraFieldValuesTest {

  @Test
  void a_text_field_is_taken_as_it_is() {
    assertThat(JiraFieldValues.toValue("Wartung")).isEqualTo("Wartung");
  }

  @Test
  void a_number_field_becomes_its_text() {
    assertThat(JiraFieldValues.toValue(42)).isEqualTo("42");
  }

  @Test
  void a_single_select_is_read_from_its_value_property() {
    assertThat(JiraFieldValues.toValue(Map.of("self", "https://jira/1", "value", "Wartung", "id", "10001")))
        .isEqualTo("Wartung");
  }

  @Test
  void a_status_is_read_from_its_name_property() {
    assertThat(JiraFieldValues.toValue(Map.of("name", "In Arbeit", "id", "3"))).isEqualTo("In Arbeit");
  }

  @Test
  void an_issue_reference_is_read_from_its_key_property() {
    assertThat(JiraFieldValues.toValue(Map.of("id", "10100", "key", "PROJ-7"))).isEqualTo("PROJ-7");
  }

  @Test
  void a_cloud_user_is_read_from_its_display_name() {
    // On Cloud a user carries neither value, nor name, nor key
    assertThat(JiraFieldValues.toValue(Map.of("accountId", "5b10a", "displayName", "A. Person")))
        .isEqualTo("A. Person");
  }

  @Test
  void a_multi_select_becomes_a_comma_separated_list() {
    assertThat(JiraFieldValues.toValue(List.of(Map.of("value", "A"), Map.of("value", "B"))))
        .isEqualTo("A,B");
  }

  @Test
  void a_list_of_plain_strings_becomes_a_comma_separated_list() {
    assertThat(JiraFieldValues.toValue(List.of("wartung", "intern"))).isEqualTo("wartung,intern");
  }

  @Test
  void an_unset_field_has_no_value() {
    assertThat(JiraFieldValues.toValue(null)).isNull();
    assertThat(JiraFieldValues.toValue("   ")).isNull();
    assertThat(JiraFieldValues.toValue(List.of())).isNull();
  }

  @Test
  void an_object_without_a_readable_property_has_no_value() {
    // rather than the toString of a Map, which is what String.valueOf used to make of it
    assertThat(JiraFieldValues.toValue(Map.of("timeSpentSeconds", 3600))).isNull();
  }

  @Test
  void a_value_beyond_the_limit_is_bounded() {
    assertThat(JiraFieldValues.toValue("x".repeat(5000))).hasSize(4000);
  }

  @Test
  void a_path_reads_the_second_level_of_a_cascading_select() {
    var fields = Map.<String, Object>of("customfield_10200",
        Map.of("value", "Wartung", "child", Map.of("value", "Hotfix")));

    assertThat(JiraFieldValues.toValue(fields, "customfield_10200")).isEqualTo("Wartung");
    assertThat(JiraFieldValues.toValue(fields, "customfield_10200.child.value")).isEqualTo("Hotfix");
  }

  @Test
  void a_path_picks_a_property_other_than_the_default_one() {
    var fields = Map.<String, Object>of("assignee",
        Map.of("displayName", "A. Person", "emailAddress", "a.person@example.com"));

    assertThat(JiraFieldValues.toValue(fields, "assignee")).isEqualTo("A. Person");
    assertThat(JiraFieldValues.toValue(fields, "assignee.emailAddress"))
        .isEqualTo("a.person@example.com");
  }

  @Test
  void a_path_over_a_list_applies_to_every_element() {
    var fields = Map.<String, Object>of("components",
        List.of(Map.of("name", "Backend"), Map.of("name", "Frontend")));

    assertThat(JiraFieldValues.toValue(fields, "components.name")).isEqualTo("Backend,Frontend");
  }

  @Test
  void a_path_that_addresses_nothing_has_no_value() {
    var fields = Map.<String, Object>of("status", Map.of("name", "In Arbeit"), "duedate", "2026-06-30");

    assertThat(JiraFieldValues.toValue(fields, "status.typo")).isNull();
    // a scalar has no parts, so a path running past the value ends nowhere
    assertThat(JiraFieldValues.toValue(fields, "duedate.somewhere")).isNull();
    assertThat(JiraFieldValues.toValue(fields, "customfield_99999.value")).isNull();
  }

  @Test
  void a_field_without_a_path_is_read_from_the_answer_directly() {
    var fields = Map.<String, Object>of("duedate", "2026-06-30");

    assertThat(JiraFieldValues.toValue(fields, "duedate")).isEqualTo("2026-06-30");
    assertThat(JiraFieldValues.toValue(fields, "missing")).isNull();
  }
}
