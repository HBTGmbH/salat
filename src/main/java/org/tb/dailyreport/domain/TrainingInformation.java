package org.tb.dailyreport.domain;

import java.time.Duration;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class TrainingInformation {

  private final long employeecontractId;
  private final long durationHours;
  private final long durationMinutes;

  public Duration toDuration() {
    return Duration.ofHours(durationHours).plusMinutes(durationMinutes);
  }

}
