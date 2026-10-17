package de.hbt.salat.invoice.domain;

import static java.time.Duration.ZERO;
import static de.hbt.salat.common.GlobalConstants.MINUTES_PER_HOUR;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * A group of the invoice with its subtotal (#1390): a suborder, a ticket, or the bookings without a
 * ticket. Groups nest — a suborder holds ticket groups, an epic the groups of its stories (#1393) —
 * and the subtotal of a group includes the groups below it. Preview, print and Excel export render
 * this one model, which is what keeps their sums the same.
 */
@Getter
public class InvoiceGroup {

  public enum Kind { SUBORDER, TICKET, NO_TICKET }

  private final Kind kind;
  /** Suborder description, {@code KEY - summary} or the reference itself; {@code null} for {@link Kind#NO_TICKET}. */
  private final String heading;
  /** The suborder of a {@link Kind#SUBORDER} group, {@code null} otherwise. */
  private final InvoiceSuborder suborder;
  /** Whether a {@link Kind#TICKET} group was reached only as the parent of booked tickets (#1393). */
  private final boolean ancestor;
  /** Lines standing directly in this group, before the groups below it. */
  private final List<InvoiceLine> lines = new ArrayList<>();
  private final List<InvoiceGroup> children = new ArrayList<>();

  /** Unique within the invoice — the preview script finds the subtotal to recompute by it. */
  @Setter
  private String id;
  @Setter
  private int depth;

  private InvoiceGroup(Kind kind, String heading, InvoiceSuborder suborder, boolean ancestor) {
    this.kind = kind;
    this.heading = heading;
    this.suborder = suborder;
    this.ancestor = ancestor;
  }

  public static InvoiceGroup ofSuborder(InvoiceSuborder suborder) {
    return new InvoiceGroup(Kind.SUBORDER, suborder.getOrderDescription(), suborder, false);
  }

  public static InvoiceGroup ofTicket(String heading, boolean ancestor) {
    return new InvoiceGroup(Kind.TICKET, heading, null, ancestor);
  }

  public static InvoiceGroup withoutTicket() {
    return new InvoiceGroup(Kind.NO_TICKET, null, null, false);
  }

  public boolean isSuborderGroup() {
    return kind == Kind.SUBORDER;
  }

  public boolean isTicketGroup() {
    return kind == Kind.TICKET;
  }

  public boolean isNoTicketGroup() {
    return kind == Kind.NO_TICKET;
  }

  /** The suborder's target hours, {@code null} for any other group. */
  public Duration getBudget() {
    return suborder != null ? suborder.getBudget() : null;
  }

  public Duration getTotalDuration() {
    return lines.stream().map(InvoiceLine::getShare).reduce(ZERO, Duration::plus)
        .plus(children.stream().map(InvoiceGroup::getTotalDuration).reduce(ZERO, Duration::plus));
  }

  public Duration getTotalDurationVisible() {
    return lines.stream().filter(InvoiceLine::isVisible).map(InvoiceLine::getShare).reduce(ZERO, Duration::plus)
        .plus(children.stream().map(InvoiceGroup::getTotalDurationVisible).reduce(ZERO, Duration::plus));
  }

  public BigDecimal getTotalHours() {
    return hours(getTotalDuration());
  }

  public BigDecimal getTotalHoursVisible() {
    return hours(getTotalDurationVisible());
  }

  /**
   * Whether print and export show the group: a suborder as long as it is selected, even when nothing
   * of it is left — as before; a ticket group only while one of its bookings is.
   */
  public boolean isVisible() {
    return suborder != null ? suborder.isVisible() : hasVisibleLines();
  }

  private boolean hasVisibleLines() {
    return lines.stream().anyMatch(InvoiceLine::isVisible) || children.stream().anyMatch(InvoiceGroup::hasVisibleLines);
  }

  private static BigDecimal hours(Duration duration) {
    return BigDecimal
        .valueOf(duration.toMinutes())
        .divide(BigDecimal.valueOf(MINUTES_PER_HOUR), 2, RoundingMode.HALF_UP);
  }
}
