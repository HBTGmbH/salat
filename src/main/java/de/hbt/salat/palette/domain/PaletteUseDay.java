package de.hbt.salat.palette.domain;

import java.time.LocalDate;

/** The runs of the palette of one login on one day (#1501). */
public record PaletteUseDay(long salatUserId, LocalDate day, long uses) {
}
