package org.tb.employee.domain;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class OvertimeReport {

  private final List<OvertimeReportMonth> months;

}
