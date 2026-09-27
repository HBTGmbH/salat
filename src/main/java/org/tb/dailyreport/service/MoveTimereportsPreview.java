package org.tb.dailyreport.service;

import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_CLOSED;

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
    LocalDate toDate,
    /** false where accepted bookings are in the range and the user is no admin (#1164) */
    boolean moveAllowed
) {

  /** The accepted bookings of the range: after the acceptance only an admin changes them (#1164). */
  public List<TimereportDTO> acceptedTimereports() {
    return timereports.stream().filter(MoveTimereportsPreview::isAccepted).toList();
  }

  static boolean isAccepted(TimereportDTO timereport) {
    return TIMEREPORT_STATUS_CLOSED.equals(timereport.getStatus());
  }

  public record NewEmployeeorderInfo(
      String employeeFullName,
      LocalDate fromDate,
      LocalDate untilDate
  ) {}
}
