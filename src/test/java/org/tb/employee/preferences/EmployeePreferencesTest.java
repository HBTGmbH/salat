package org.tb.employee.preferences;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class EmployeePreferencesTest {

  @Test
  void from_should_parse_notificationEmail() {
    Map<String, Object> map = Map.of(EmployeePreferences.KEY_NOTIFICATION_EMAIL, "user@example.com");

    EmployeePreferences prefs = EmployeePreferences.from(map);

    assertThat(prefs.notificationEmail()).isEqualTo("user@example.com");
  }

  @Test
  void from_should_parse_gravatarEmail() {
    Map<String, Object> map = Map.of(EmployeePreferences.KEY_GRAVATAR_EMAIL, "gravatar@example.com");

    EmployeePreferences prefs = EmployeePreferences.from(map);

    assertThat(prefs.gravatarEmail()).isEqualTo("gravatar@example.com");
  }

  @Test
  void from_should_return_defaults_for_empty_map() {
    EmployeePreferences prefs = EmployeePreferences.from(Map.of());

    assertThat(prefs).isEqualTo(EmployeePreferences.defaults());
  }

  @Test
  void defaults_should_have_null_emails() {
    EmployeePreferences defaults = EmployeePreferences.defaults();

    assertThat(defaults.notificationEmail()).isNull();
    assertThat(defaults.gravatarEmail()).isNull();
  }

  @Test
  void toMap_should_roundtrip_via_from() {
    EmployeePreferences original = new EmployeePreferences("notify@example.com", "gravatar@example.com");

    EmployeePreferences roundtripped = EmployeePreferences.from(original.toMap());

    assertThat(roundtripped.notificationEmail()).isEqualTo(original.notificationEmail());
    assertThat(roundtripped.gravatarEmail()).isEqualTo(original.gravatarEmail());
  }

  @Test
  void toMap_should_omit_blank_emails() {
    EmployeePreferences prefs = new EmployeePreferences("", "  ");

    Map<String, Object> map = prefs.toMap();

    assertThat(map).doesNotContainKey(EmployeePreferences.KEY_NOTIFICATION_EMAIL);
    assertThat(map).doesNotContainKey(EmployeePreferences.KEY_GRAVATAR_EMAIL);
  }

  @Test
  void toMap_should_omit_null_emails() {
    EmployeePreferences prefs = EmployeePreferences.defaults();

    Map<String, Object> map = prefs.toMap();

    assertThat(map).doesNotContainKey(EmployeePreferences.KEY_NOTIFICATION_EMAIL);
    assertThat(map).doesNotContainKey(EmployeePreferences.KEY_GRAVATAR_EMAIL);
  }

}
