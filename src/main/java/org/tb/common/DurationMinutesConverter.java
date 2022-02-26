package org.tb.common;

import java.time.Duration;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class DurationMinutesConverter implements AttributeConverter<Duration, Integer> {

  @Override
  public Integer convertToDatabaseColumn(Duration duration) {
    return (int) duration.toMinutes();
  }

  @Override
  public Duration convertToEntityAttribute(Integer minutes) {
    return Duration.ofMinutes(minutes);
  }

}
