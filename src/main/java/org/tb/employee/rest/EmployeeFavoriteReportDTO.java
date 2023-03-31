package org.tb.employee.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EmployeeFavoriteReportDTO implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;
  @Schema(nullable = true)
  private Long id;

  @Schema(example = "12345")
  private Long employeeorderId;

  @Schema(accessMode = AccessMode.READ_ONLY)
  private List<String> orderLabelPath;
  @Schema(accessMode = AccessMode.READ_ONLY)
  private List<String> orderSignPath;

  @Schema(example = "0")
  private Integer hours;
  @Schema(example = "30")
  private Integer minutes;

  @Schema(example = "Daily")
  private String comment;
}