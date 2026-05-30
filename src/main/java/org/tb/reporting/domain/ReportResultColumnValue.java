package org.tb.reporting.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class ReportResultColumnValue implements Serializable {

  private final Object value;

  public boolean isNumeric() {
    return value instanceof Number;
  }

  public String getFormattedValue(Locale locale) {
    if (value == null) return "";
    if (value instanceof BigDecimal || value instanceof Double || value instanceof Float) {
      return NumberFormat.getInstance(locale).format(value);
    }
    return String.valueOf(value);
  }

  public String getValueAsString() {
    return String.valueOf(value);
  }

}
