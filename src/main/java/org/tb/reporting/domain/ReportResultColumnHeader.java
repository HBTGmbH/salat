package org.tb.reporting.domain;

import java.io.Serializable;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class ReportResultColumnHeader implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String name;

}
