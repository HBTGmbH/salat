package org.tb.settings.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Converter(autoApply = false)
public class UserPreferenceConverter implements AttributeConverter<UserPreferenceMap, String> {

  private static final TypeReference<Map<String, Map<String, Object>>> MAP_TYPE =
      new TypeReference<>() {};

  private static final ObjectMapper objectMapper = new ObjectMapper()
      .findAndRegisterModules()
      .disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
      .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);

  @Override
  public String convertToDatabaseColumn(UserPreferenceMap map) {
    if (map == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(map.asRawMap());
    } catch (Exception ex) {
      log.error("Failed to serialize UserPreferenceMap to JSON", ex);
      return null;
    }
  }

  @Override
  public UserPreferenceMap convertToEntityAttribute(String json) {
    if (json == null || json.isBlank()) {
      return UserPreferenceMap.empty();
    }
    try {
      Map<String, Map<String, Object>> raw = objectMapper.readValue(json, MAP_TYPE);
      return new UserPreferenceMap(raw);
    } catch (Exception ex) {
      log.error("Failed to deserialize UserPreferenceMap from JSON: {}", json, ex);
      return UserPreferenceMap.empty();
    }
  }

}
