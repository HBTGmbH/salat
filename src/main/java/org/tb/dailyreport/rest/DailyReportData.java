package org.tb.dailyreport.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DailyReportData {

  private long employeeorderId;
  @Schema(accessMode = AccessMode.READ_ONLY)
  private String orderLabel;
  @Schema(accessMode = AccessMode.READ_ONLY)
  private String suborderLabel;
  private long hours;
  private long minutes;
  private String comment;

  private boolean training;
  @Schema(accessMode = AccessMode.READ_ONLY)
  private String suborderSign;
  @Schema(accessMode = AccessMode.READ_ONLY)
  private String orderSign;
  @NotNull
  private String date;

}
