package org.tb.common.palette;

/**
 * The parameter types of a palette command the server completes (#1158). Duration and comment are
 * no member: the browser reads a duration itself, and a comment is free text — its ticket
 * suggestions come from the endpoint the booking form asks as well.
 */
public enum PaletteParameter {
  /** Only the last working day; every other day the browser reads itself. */
  DAY,
  MONTH,
  SUBORDER,
  PERSON,
  CUSTOMERORDER
}
