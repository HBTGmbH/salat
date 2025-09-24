package org.tb.etl.domain;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SqlStatements {

  private List<String> statements;

}
