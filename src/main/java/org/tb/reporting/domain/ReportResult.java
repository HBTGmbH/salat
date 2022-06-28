package org.tb.reporting.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ReportResult implements Serializable {

  private static final long serialVersionUID = 1L;

  private final List<ReportResultColumnHeader> columnHeaders = new ArrayList<>();
  private final List<ReportResultRow> rows = new ArrayList<>();

}
