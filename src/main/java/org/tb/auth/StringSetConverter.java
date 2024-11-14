package org.tb.auth;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Converter(autoApply = true)
public class StringSetConverter implements AttributeConverter<Set<String>, String> {

  @Override
  public String convertToDatabaseColumn(Set<String> values) {
    if(values == null || values.isEmpty()) return null;
    return values.stream().collect(Collectors.joining(","));
  }

  @Override
  public Set<String> convertToEntityAttribute(String flatString) {
    if(flatString == null || flatString.isBlank()) {
      return new HashSet<>(); // must be a modifiable Set
    }
    String[] parts = flatString.split(",");

    // must be a modifiable Set
    return new HashSet(Stream.of(parts).map(String::trim).toList());
  }

}
