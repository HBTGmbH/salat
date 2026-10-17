package de.hbt.salat.invoice.domain;

/** Where a line names its tickets (#1390). */
public enum InvoiceTicketDisplay {

  /** In a column of its own, „Ticket“ — the default. */
  COLUMN,

  /** Put in front of the comment, as far as the comment does not name them already. */
  COMMENT,

  /** Not at all, as before ticket references existed. */
  NONE
}
