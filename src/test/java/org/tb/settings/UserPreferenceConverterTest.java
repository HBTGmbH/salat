package org.tb.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.settings.domain.UserPreferenceConverter;
import org.tb.settings.domain.UserPreferenceMap;

@DisplayNameGeneration(ReplaceUnderscores.class)
public class UserPreferenceConverterTest {

  private final UserPreferenceConverter converter = new UserPreferenceConverter();

  @Test
  void should_serialize_map_to_json() {
    UserPreferenceMap map = new UserPreferenceMap(
        Map.of("daily", Map.of("workDayStart", "08:30")));

    String json = converter.convertToDatabaseColumn(map);

    assertThat(json).isNotNull()
        .contains("\"daily\"")
        .contains("\"workDayStart\"");
  }

  @Test
  void should_deserialize_json_to_map() {
    String json = "{\"daily\":{\"workDayStart\":\"08:30\"}}";

    UserPreferenceMap map = converter.convertToEntityAttribute(json);

    assertThat(map.getModule("daily")).containsEntry("workDayStart", "08:30");
  }

  @Test
  void should_roundtrip_serialize_and_deserialize() {
    UserPreferenceMap original = new UserPreferenceMap(
        Map.of("daily", Map.of("workDayStart", "07:15")));

    String json = converter.convertToDatabaseColumn(original);
    UserPreferenceMap deserialized = converter.convertToEntityAttribute(json);

    assertThat(deserialized.getModule("daily")).isEqualTo(original.getModule("daily"));
  }

  @Test
  void should_handle_null_entity_attribute() {
    String json = converter.convertToDatabaseColumn(null);

    assertThat(json).isNull();
  }

  @Test
  void should_handle_null_database_column_as_empty() {
    UserPreferenceMap map = converter.convertToEntityAttribute(null);

    assertThat(map.asRawMap()).isEmpty();
  }

  @Test
  void should_handle_blank_string_as_empty() {
    UserPreferenceMap map = converter.convertToEntityAttribute("   ");

    assertThat(map.asRawMap()).isEmpty();
  }

  @Test
  void should_recover_from_invalid_json() {
    UserPreferenceMap map = converter.convertToEntityAttribute("{invalid json}");

    assertThat(map.asRawMap()).isEmpty();
  }

  @Test
  void should_handle_multiple_modules() {
    UserPreferenceMap original = new UserPreferenceMap(Map.of(
        "daily", Map.of("workDayStart", "09:00"),
        "other", Map.of("someKey", "someValue")));

    String json = converter.convertToDatabaseColumn(original);
    UserPreferenceMap deserialized = converter.convertToEntityAttribute(json);

    assertThat(deserialized.getModule("daily")).containsEntry("workDayStart", "09:00");
    assertThat(deserialized.getModule("other")).containsEntry("someKey", "someValue");
  }

  @Test
  void should_return_empty_map_for_missing_module() {
    UserPreferenceMap map = new UserPreferenceMap(Map.of("daily", Map.of("k", "v")));

    assertThat(map.getModule("nonexistent")).isEmpty();
  }

}
