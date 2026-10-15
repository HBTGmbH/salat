package de.hbt.salat.dailyreport.domain;

import java.time.Duration;

/**
 * The booked working time of a contract in a range and the time surcharge credit on it (#1469), summed in
 * one query: the overtime account needs both over the same bookings.
 *
 * @param minutes                the booked working time, {@code null} without bookings
 * @param surchargeCreditMinutes the time surcharge credit, {@code null} without bookings
 */
public record ReportedMinutes(Long minutes, Long surchargeCreditMinutes) {

  public Duration workingTime() {
    return Duration.ofMinutes(minutes != null ? minutes : 0L);
  }

  public Duration surchargeCredit() {
    return Duration.ofMinutes(surchargeCreditMinutes != null ? surchargeCreditMinutes : 0L);
  }
}
