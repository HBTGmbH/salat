package org.tb.settings.domain;

import static org.tb.common.GlobalConstants.DEFAULT_WORK_DAY_START;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalTime;

public record UserSettings(
    @JsonProperty("workDayStart")
    LocalTime workDayStart
) {

  public static UserSettings defaults() {
    return new UserSettings(LocalTime.of(DEFAULT_WORK_DAY_START, 0));
  }

}
