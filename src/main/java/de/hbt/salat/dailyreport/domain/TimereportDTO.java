package de.hbt.salat.dailyreport.domain;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.SurchargeKind;

/**
 * Timereports are very sensitive information. Thus we need to care about time reports in a special kind of way
 * to protect the data from unintended access or modification. This is why we are using a special class to
 * copy the entity data to a DTO and use it outside the {@link de.hbt.salat.dailyreport.service.TimereportService}.
 *
 * <p>{@link Timereport} entities stay inside this module: they are not handed to another module, and
 * another module's query may join them but must return a record or DTO of its own rather than the
 * entity (→ ADR-0021). This DTO is the copy for callers that want the whole booking; a module that
 * needs three of its columns is better served by a record naming those three.
 */
@Data
@Builder
@Jacksonized
@AllArgsConstructor
public class TimereportDTO implements Serializable {

  private final long id;
  private final LocalDate referenceday;
  private final long employeeId;
  private final String employeeName;
  private final String employeeSign;
  private final long employeecontractId;
  private final String completeOrderSign;
  private final String customerorderSign;
  private final String customerorderDescription;
  private final long suborderId;
  private final String suborderDescription;
  private final OrderType orderType;
  private final String customerShortname;
  private final long customerorderId;
  private final long employeeorderId;
  private final Duration duration;
  private final String taskdescription;
  /** The ticket references of the booking in their order (#982, #1326); empty when there are none. */
  @Builder.Default
  private final List<String> ticketReferences = List.of();
  private final String status;
  private final boolean training;
  /** A night, Saturday, Sunday or holiday assignment (#1469), {@code null} for none. */
  private final SurchargeKind surchargeKind;
  /** The rate stored with the flag, {@code null} without one. */
  private final Integer surchargePercent;
  /** The credit on the overtime account, duration × rate; zero without a flag. */
  @Builder.Default
  private final Duration surchargeCredit = Duration.ZERO;
  private final int sequencenumber;
  private final String releasedby;
  private final LocalDateTime released;
  private final String acceptedby;
  private final LocalDateTime accepted;
  private final String timeReportAsString;
  private final String employeeOrderAsString;
  private final boolean billable;
  private final LocalDateTime created;
  private final LocalDateTime lastupdate;
  private final String createdby;
  private final String lastupdatedby;
  private final boolean fitsToContract;

  public boolean matches5MinuteSchema() {
    return duration.toMinutesPart() % 5 == 0;
  }

  /** Standby, and therefore not part of any working time sum (#463). */
  public boolean isStandby() {
    return orderType == OrderType.BEREITSCHAFT;
  }

  /** The duration as far as it counts as working time — {@link Duration#ZERO} for standby (#463). */
  public Duration getWorkingTime() {
    return isStandby() ? Duration.ZERO : duration;
  }

  /**
   * The time surcharge credit as the overtime account counts it (#1469): none on standby, which is no working
   * time — a flag kept from before its order became standby earns nothing there either.
   */
  public Duration getCountedSurchargeCredit() {
    return isStandby() ? Duration.ZERO : surchargeCredit;
  }

  /**
   * The booking as budget, controlling and an invoice that includes the surcharges count it (#1470): its
   * duration plus the time surcharge credit — the credit as the overtime account counts it, so none on
   * standby. The customer is charged the rate the person is credited with, and the controlling sees no
   * figure of its own for it: the surcharge is part of the hours.
   */
  public Duration getDurationWithSurcharge() {
    return durationWithSurcharge(duration, surchargeCredit, orderType);
  }

  /**
   * {@link #getDurationWithSurcharge} for whoever reads the three columns without the DTO — a query of the
   * budget module, say. {@code orderType} is the effective one, that of the suborder before that of the order.
   */
  public static Duration durationWithSurcharge(Duration duration, Duration surchargeCredit, OrderType orderType) {
    var counted = surchargeCredit == null || orderType == OrderType.BEREITSCHAFT ? Duration.ZERO : surchargeCredit;
    return duration.plus(counted);
  }

  /**
   * What the booking adds to the time account and holds against the target: its working time plus its time
   * surcharge credit (#1469), as the overtime account counts it. The booked hours stay {@link #getWorkingTime}.
   */
  public Duration getTimeAccountTime() {
    return getWorkingTime().plus(getCountedSurchargeCredit());
  }

  /** {@link #getTimeAccountTime} of {@code timereports} up to {@code until} - what matrix and month hold against the target. */
  public static Duration timeAccountTimeUntil(Collection<TimereportDTO> timereports, LocalDate until) {
    return timereports.stream()
        .filter(r -> !r.getReferenceday().isAfter(until))
        .map(TimereportDTO::getTimeAccountTime)
        .reduce(Duration.ZERO, Duration::plus);
  }

  /** {@link #getCountedSurchargeCredit} of {@code timereports} - the sum next to a total. */
  public static Duration surchargeCreditOf(Collection<TimereportDTO> timereports) {
    return timereports.stream().map(TimereportDTO::getCountedSurchargeCredit).reduce(Duration.ZERO, Duration::plus);
  }

}
