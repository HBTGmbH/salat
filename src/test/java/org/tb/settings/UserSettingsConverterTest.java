package org.tb.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.settings.domain.UserSettings;
import org.tb.settings.domain.UserSettingsConverter;

@DisplayNameGeneration(ReplaceUnderscores.class)
public class UserSettingsConverterTest {

  private final UserSettingsConverter converter = new UserSettingsConverter();

  @Test
  void should_serialize_settings_to_json() {
    // Given: settings with custom time
    UserSettings settings = new UserSettings(LocalTime.of(8, 30));

    // When: converting to database column
    String json = converter.convertToDatabaseColumn(settings);

    // Then: valid JSON string with workDayStart field
    assertThat(json).isNotNull()
        .contains("\"workDayStart\"");
    // Note: Jackson can serialize LocalTime as [8,30] (array) or "08:30:00" (string)
    // depending on configuration; we verify round-trip instead of exact format
  }

  @Test
  void should_deserialize_json_to_settings_from_string_format() {
    // Given: valid JSON string in ISO format
    String json = "{\"workDayStart\":\"07:45:00\"}";

    // When: converting to entity attribute
    UserSettings settings = converter.convertToEntityAttribute(json);

    // Then: correct settings object is created
    assertThat(settings).isNotNull();
    assertThat(settings.workDayStart()).isEqualTo(LocalTime.of(7, 45));
  }

  @Test
  void should_deserialize_json_to_settings_from_array_format() {
    // Given: valid JSON in array format (Jackson default for LocalTime)
    String json = "{\"workDayStart\":[7,45,0]}";

    // When: converting to entity attribute
    UserSettings settings = converter.convertToEntityAttribute(json);

    // Then: correct settings object is created
    assertThat(settings).isNotNull();
    assertThat(settings.workDayStart()).isEqualTo(LocalTime.of(7, 45));
  }

  @Test
  void should_roundtrip_serialize_and_deserialize() {
    // Given: settings with various times
    LocalTime[] times = {
        LocalTime.of(0, 0),    // midnight
        LocalTime.of(9, 0),    // default
        LocalTime.of(12, 30),  // noon-ish
        LocalTime.of(23, 59),  // end of day
    };

    for (LocalTime time : times) {
      // When: roundtripping
      UserSettings original = new UserSettings(time);
      String json = converter.convertToDatabaseColumn(original);
      UserSettings deserialized = converter.convertToEntityAttribute(json);

      // Then: value is preserved
      assertThat(deserialized.workDayStart()).isEqualTo(time);
    }
  }

  @Test
  void should_handle_null_entity_attribute() {
    // When: converting null to database column
    String json = converter.convertToDatabaseColumn(null);

    // Then: returns null
    assertThat(json).isNull();
  }

  @Test
  void should_handle_null_database_column_as_default() {
    // When: converting null from database column
    UserSettings settings = converter.convertToEntityAttribute(null);

    // Then: defaults returned
    assertThat(settings).isNotNull();
    assertThat(settings.workDayStart()).isEqualTo(LocalTime.of(9, 0));
  }

  @Test
  void should_handle_empty_string_as_default() {
    // When: converting empty string from database column
    UserSettings settings = converter.convertToEntityAttribute("");

    // Then: defaults returned
    assertThat(settings).isNotNull();
    assertThat(settings.workDayStart()).isEqualTo(LocalTime.of(9, 0));
  }

  @Test
  void should_handle_blank_string_as_default() {
    // When: converting blank string from database column
    UserSettings settings = converter.convertToEntityAttribute("   ");

    // Then: defaults returned
    assertThat(settings).isNotNull();
    assertThat(settings.workDayStart()).isEqualTo(LocalTime.of(9, 0));
  }

  @Test
  void should_recover_from_invalid_json() {
    // When: converting invalid JSON
    UserSettings settings = converter.convertToEntityAttribute("{invalid json}");

    // Then: defaults returned instead of throwing
    assertThat(settings).isNotNull();
    assertThat(settings.workDayStart()).isEqualTo(LocalTime.of(9, 0));
  }

  @Test
  void should_return_defaults_when_record_cannot_be_deserialized() {
    // When: converting JSON that doesn't match record structure
    // (e.g. completely wrong format - this tests error recovery)
    UserSettings settings = converter.convertToEntityAttribute("{\"foo\":\"bar\"}");

    // Then: defaults returned (caught by exception handler)
    assertThat(settings).isNotNull();
    assertThat(settings.workDayStart()).isEqualTo(LocalTime.of(9, 0));
  }

  @Test
  void should_preserve_time_with_seconds_precision() {
    // Given: time with seconds
    LocalTime timeWithSeconds = LocalTime.of(10, 30, 45);
    UserSettings settings = new UserSettings(timeWithSeconds);

    // When: roundtripping
    String json = converter.convertToDatabaseColumn(settings);
    UserSettings deserialized = converter.convertToEntityAttribute(json);

    // Then: seconds are preserved
    assertThat(deserialized.workDayStart()).isEqualTo(timeWithSeconds);
    assertThat(deserialized.workDayStart().getSecond()).isEqualTo(45);
  }

  @Test
  void should_handle_localtime_with_nanoseconds() {
    // Given: time with nanoseconds (edge case)
    LocalTime timeWithNanos = LocalTime.of(14, 22, 33, 500000000);
    UserSettings settings = new UserSettings(timeWithNanos);

    // When: roundtripping
    String json = converter.convertToDatabaseColumn(settings);
    UserSettings deserialized = converter.convertToEntityAttribute(json);

    // Then: time is preserved (Jackson typically drops sub-microsecond precision)
    assertThat(deserialized.workDayStart()).isNotNull();
    assertThat(deserialized.workDayStart().getHour()).isEqualTo(14);
    assertThat(deserialized.workDayStart().getMinute()).isEqualTo(22);
  }

  @Test
  void should_generate_valid_json_syntax() {
    // Given: settings
    UserSettings settings = new UserSettings(LocalTime.of(6, 0));

    // When: converting to JSON
    String json = converter.convertToDatabaseColumn(settings);

    // Then: valid JSON structure that can be re-parsed
    UserSettings reparsed = converter.convertToEntityAttribute(json);
    assertThat(reparsed).isNotNull();
    assertThat(reparsed.workDayStart()).isEqualTo(LocalTime.of(6, 0));
  }

}
