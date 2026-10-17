package de.hbt.salat.invoice.domain;

/**
 * What the lines of an invoice are grouped by (#1390). {@link #SUBORDER} is the invoice as it was
 * before ticket references existed, and the default.
 */
public enum InvoiceGrouping {

  /** Order → suborder → booking. */
  SUBORDER,

  /** Order → suborder → ticket: within every suborder, its bookings by ticket. */
  SUBORDER_TICKET,

  /** Order → ticket: the bookings by ticket, regardless of the suborder they were booked on. */
  TICKET;

  public boolean isByTicket() {
    return this != SUBORDER;
  }
}
