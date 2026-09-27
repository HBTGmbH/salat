package org.tb.palette.service;

import java.util.List;
import org.tb.common.palette.PaletteHit;
import org.tb.common.palette.PaletteKind;

/** The hits of one kind, in the order the palette shows them. */
public record PaletteGroup(PaletteKind kind, List<PaletteHit> hits) {
}
