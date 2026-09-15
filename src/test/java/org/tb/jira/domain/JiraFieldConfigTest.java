package org.tb.jira.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * How the two configured lists are read (#881).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraFieldConfigTest {

  @Test
  void an_unused_configuration_has_no_hash() {
    // and a ticket written without one is therefore left alone, run after run
    var config = JiraFieldConfig.from(configWith(null, null));

    assertThat(config.isEmpty()).isTrue();
    assertThat(config.hash()).isNull();
  }

  @Test
  void an_inherited_entry_is_requested_even_when_it_was_only_named_there() {
    // otherwise it would never be read from JIRA and stay silently empty
    var config = JiraFieldConfig.from(configWith("customfield_10123", "customfield_10124"));

    assertThat(config.fieldPaths()).containsExactly("customfield_10123", "customfield_10124");
    assertThat(config.inheritedFieldPaths()).containsExactly("customfield_10124");
  }

  @Test
  void several_paths_into_one_field_are_one_request_key() {
    // JIRA only accepts top-level ids in its fields parameter
    var config = JiraFieldConfig.from(
        configWith("customfield_10200, customfield_10200.child.value, status.name", null));

    assertThat(config.requestKeys()).containsExactly("customfield_10200", "status");
  }

  @Test
  void the_hash_ignores_spacing_and_order_but_not_the_list_an_entry_is_in() {
    var spaced = JiraFieldConfig.from(configWith(" status , customfield_10123 ", null));
    var reordered = JiraFieldConfig.from(configWith("customfield_10123,status", null));
    var inherited = JiraFieldConfig.from(configWith("customfield_10123,status", "status"));

    assertThat(spaced.hash()).isEqualTo(reordered.hash());
    // moving an entry into the inherited list changes what the effective column holds
    assertThat(inherited.hash()).isNotEqualTo(reordered.hash());
  }

  @Test
  void an_entry_with_an_empty_segment_is_dropped() {
    // a stray or trailing dot can only ever address nothing
    var config = JiraFieldConfig.from(configWith("status., .name, customfield_10123", null));

    assertThat(config.fieldPaths()).containsExactly("customfield_10123");
  }

  private static JiraReplicationConfig configWith(String additional, String inherited) {
    var config = new JiraReplicationConfig();
    config.setAdditionalFieldNames(additional);
    config.setInheritedFieldNames(inherited);
    return config;
  }
}
