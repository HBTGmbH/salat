package org.tb.auth.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.tb.auth.domain.AccessLevel;

@Converter(autoApply = true)
public class AccessLevelSetConverter implements AttributeConverter<Set<AccessLevel>, String> {

  @Override
  public String convertToDatabaseColumn(Set<AccessLevel> accessLevels) {
    if(accessLevels == null || accessLevels.isEmpty()) return null;
    return accessLevels.stream().map(AccessLevel::name).collect(Collectors.joining(","));
  }

  @Override
  public Set<AccessLevel> convertToEntityAttribute(String flatString) {
    if(flatString == null || flatString.isBlank()) {
      return new HashSet<>(); // must be a modifiable Set
    }
    String[] parts = flatString.split(",");

    // must be a modifiable Set
    return new HashSet<>(Stream.of(parts).map(String::trim).map(AccessLevel::valueOf).toList());
  }

}
