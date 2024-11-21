package org.tb.dailyreport.domain;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class OvertimeReport {

  private final OvertimeReportTotal total;
  private final List<OvertimeReportMonth> months;

}
