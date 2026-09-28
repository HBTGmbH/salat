package org.tb.common.palette;

/**
 * The commands of the palette that take parameters (#1158). The browser reads what is typed and
 * builds the target; the server only completes the parameters it alone can answer, and for that it
 * has to know which command asks — a month means the next open one for {@link #RELEASE}, the next
 * one to accept for {@link #ACCEPT}.
 *
 * <p>Which of them a user is offered, the sidebar decides (ADR-0030): each is bound to the entry of
 * its page.
 */
public enum PaletteCommand {
  BOOK,
  DAY,
  MATRIX,
  RELEASE,
  ACCEPT,
  CONTROLLING
}
