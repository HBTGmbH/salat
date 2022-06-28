package org.tb.reporting.domain;

import java.io.Serializable;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class ReportResultColumnValue implements Serializable {

  private final Object value;

  public String getValueAsString() {
    return String.valueOf(value);
  }

}
