package de.hbt.salat.invoice.domain;

import static de.hbt.salat.common.GlobalConstants.MINUTES_PER_HOUR;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import de.hbt.salat.common.util.TicketReferences;

/**
 * A booking as it stands in one group of the invoice (#1390) — in full, or with its share when its
 * duration is divided among several tickets. A booking divided that way has one line in each of the
 * groups its shares fall into, and the shares add up to its duration.
 *
 * <p>Whether a line counts is decided by the booking and its suborder, never by the line itself: all
 * lines of a booking are in the invoice or none is.
 */
@Getter
public class InvoiceLine {

  private final InvoiceTimereport timereport;
  private final InvoiceSuborder suborder;
  private Duration share;
  /** What the line names as its tickets: the share's ones in a ticket group, all of the booking's elsewhere. */
  private final List<String> ticketReferences;

  /** The ids of the groups the line stands in, outermost first, separated by blanks — for the preview script. */
  @Setter
  private String groupPath = "";

  private InvoiceLine(InvoiceTimereport timereport, InvoiceSuborder suborder, Duration share, List<String> ticketReferences) {
    this.timereport = timereport;
    this.suborder = suborder;
    this.share = share;
    this.ticketReferences = new ArrayList<>(ticketReferences);
  }

  /** The booking with its whole duration and all its references. */
  public static InvoiceLine whole(InvoiceTimereport timereport, InvoiceSuborder suborder) {
    return new InvoiceLine(timereport, suborder, timereport.getDuration(), timereport.getTicketReferences());
  }

  /** The booking with nothing yet; {@link #add} gives it its shares. */
  public static InvoiceLine empty(InvoiceTimereport timereport, InvoiceSuborder suborder) {
    return new InvoiceLine(timereport, suborder, Duration.ZERO, List.of());
  }

  /** A further share of the same booking in the same group: one line with the sum of the shares (#1393). */
  public void add(Duration share, String ticketReference) {
    this.share = this.share.plus(share);
    this.ticketReferences.add(ticketReference);
  }

  public boolean isVisible() {
    return suborder.isVisible() && timereport.isVisible();
  }

  /** Whether the line carries only part of the booking. */
  public boolean isShared() {
    return !share.equals(timereport.getDuration());
  }

  public BigDecimal getHours() {
    return BigDecimal
        .valueOf(share.toMinutes())
        .divide(BigDecimal.valueOf(MINUTES_PER_HOUR), 2, RoundingMode.HALF_UP);
  }

  public String getTicketText() {
    return String.join(", ", ticketReferences);
  }

  /**
   * The comment with the line's tickets in front of it, as far as it does not name them already — the
   * display „im Kommentar“.
   */
  public String getTaskDescriptionWithTickets() {
    var description = timereport.getTaskDescription();
    var missing = TicketReferences.referencesNotIn(description, ticketReferences);
    if (missing.isEmpty()) {
      return description;
    }
    var prefix = String.join(", ", missing);
    return description == null || description.isBlank() ? prefix : prefix + " " + description;
  }
}
