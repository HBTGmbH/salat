package de.hbt.salat.common.palette;

/**
 * Where a hit of the palette leads: a page, or a form that opens prefilled. Never an action — the
 * palette saves nothing (ADR-0030). {@code rank} orders the targets of one hit; the lowest is the
 * one Enter opens.
 */
public record PaletteTarget(PaletteText label, String href, int rank) {

  /** The rank of the target that opens the object itself. */
  public static final int OPEN = 0;
}
