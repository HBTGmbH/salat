package org.tb.dailyreport.service;

import java.time.LocalDate;
import java.util.List;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.order.domain.Suborder;

public record MoveTimereportsPreview(
    List<TimereportDTO> timereports,
    List<NewEmployeeorderInfo> newEmployeeOrders,
    Suborder sourceSuborder,
    Suborder targetSuborder,
    LocalDate fromDate,
    LocalDate toDate
) {

  public record NewEmployeeorderInfo(
      String employeeFullName,
      LocalDate fromDate,
      LocalDate untilDate
  ) {}
}
