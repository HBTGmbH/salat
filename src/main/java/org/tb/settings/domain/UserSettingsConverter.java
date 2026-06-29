package org.tb.settings.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Converter(autoApply = false)
public class UserSettingsConverter implements AttributeConverter<UserSettings, String> {

  private static final ObjectMapper objectMapper = new ObjectMapper()
      .findAndRegisterModules()
      .disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
      .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);

  @Override
  public String convertToDatabaseColumn(UserSettings settings) {
    if (settings == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(settings);
    } catch (Exception ex) {
      log.error("Failed to convert UserSettings to JSON", ex);
      return null;
    }
  }

  @Override
  public UserSettings convertToEntityAttribute(String json) {
    if (json == null || json.isBlank()) {
      return UserSettings.defaults();
    }
    try {
      return objectMapper.readValue(json, UserSettings.class);
    } catch (Exception ex) {
      log.error("Failed to deserialize UserSettings from JSON: {}", json, ex);
      return UserSettings.defaults();
    }
  }

}
