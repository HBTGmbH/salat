package org.tb.dailyreport.domain;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class TrainingInformation {

  private final long employeecontractId;
  private final long durationHours;
  private final long durationMinutes;

}
