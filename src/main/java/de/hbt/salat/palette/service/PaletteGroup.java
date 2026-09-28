package de.hbt.salat.palette.service;

import java.util.List;
import de.hbt.salat.common.palette.PaletteHit;
import de.hbt.salat.common.palette.PaletteKind;

/** The hits of one kind, in the order the palette shows them. */
public record PaletteGroup(PaletteKind kind, List<PaletteHit> hits) {
}
