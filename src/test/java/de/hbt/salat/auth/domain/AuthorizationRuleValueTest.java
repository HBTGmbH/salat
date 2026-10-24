package de.hbt.salat.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/** What the rule list shows for an object (#1266, #1523). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class AuthorizationRuleValueTest {

  /** Two reports of the same title in different groups are told apart in the list. */
  @Test
  void an_object_in_a_group_is_shown_with_its_group() {
    var value = AuthorizationRuleValue.of(new AuthorizationObject("42", "Umsatz", null, "Controlling"));

    assertThat(value.shown()).isEqualTo("Controlling · Umsatz");
    assertThat(value.group()).isEqualTo("Controlling");
  }

  @Test
  void an_object_without_a_group_is_shown_as_before() {
    assertThat(AuthorizationRuleValue.of(new AuthorizationObject("42", "Umsatz")).shown()).isEqualTo("Umsatz");
    assertThat(AuthorizationRuleValue.of(new AuthorizationObject("7", "Person A | pa", "pa")).shown()).isEqualTo("pa");
    assertThat(new AuthorizationRuleValue("*", null, null, false).shown()).isEqualTo("*");
  }
}
