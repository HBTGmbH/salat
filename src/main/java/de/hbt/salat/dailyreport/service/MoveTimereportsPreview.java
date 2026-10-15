package de.hbt.salat.dailyreport.service;

import static de.hbt.salat.common.GlobalConstants.TIMEREPORT_STATUS_CLOSED;

import java.time.LocalDate;
import java.util.List;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.order.domain.Suborder;

public record MoveTimereportsPreview(
    List<TimereportDTO> timereports,
    List<NewEmployeeorderInfo> newEmployeeOrders,
    Suborder sourceSuborder,
    Suborder targetSuborder,
    LocalDate fromDate,
    LocalDate toDate,
    /**
     * false where accepted bookings are in the range and the user is no admin (#1164), or where a flagged booking
     * carries a time surcharge the target does not offer (#1469)
     */
    boolean moveAllowed,
    /** the flagged bookings and the rate the target offers for their kind (#1469) */
    List<SurchargeChange> surchargeChanges
) {

  /** The flagged bookings the move refuses: the target offers no surcharge of their kind (#1469). */
  public List<SurchargeChange> refusedSurcharges() {
    return surchargeChanges.stream().filter(SurchargeChange::refused).toList();
  }

  /** The flagged bookings that take the target's rate for their kind, another than their own (#1469). */
  public List<SurchargeChange> changedSurchargeRates() {
    return surchargeChanges.stream().filter(SurchargeChange::rateChanges).toList();
  }

  /**
   * A flagged booking and what the move does to its time surcharge (#1469): on another suborder the rate is
   * the one it offers there, as for an edit that changes the suborder.
   *
   * @param targetPercent the rate the target offers for the booking's kind, {@code null} for none
   */
  public record SurchargeChange(TimereportDTO timereport, Integer targetPercent) {

    public boolean refused() {
      return targetPercent == null;
    }

    public boolean rateChanges() {
      return targetPercent != null && !targetPercent.equals(timereport.getSurchargePercent());
    }
  }

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
