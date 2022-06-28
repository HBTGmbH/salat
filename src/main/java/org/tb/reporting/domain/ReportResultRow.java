package org.tb.reporting.domain;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;

@Data
public class ReportResultRow implements Serializable {

  private static final long serialVersionUID = 1L;

  private final Map<String, ReportResultColumnValue> columnValues = new HashMap<>();

}
