package org.tb.common;

import java.time.Duration;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class DurationMinutesConverter implements AttributeConverter<Duration, Integer> {

  @Override
  public Integer convertToDatabaseColumn(Duration duration) {
    if(duration == null) return null;
    return (int) duration.toMinutes();
  }

  @Override
  public Duration convertToEntityAttribute(Integer minutes) {
    if(minutes == null) return null;
    return Duration.ofMinutes(minutes);
  }

}
