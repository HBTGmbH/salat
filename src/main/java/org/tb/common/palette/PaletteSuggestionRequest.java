package org.tb.common.palette;

import java.time.LocalDate;

/**
 * What a parameter of a palette command is to be completed with (#1158).
 *
 * @param command    the command the parameter belongs to
 * @param parameter  the parameter
 * @param query      what was typed for it; may be empty — then the provider offers what it would
 *                   put first
 * @param date       the day chosen before, where the command has one ({@link PaletteCommand#BOOK});
 *                   {@code null} otherwise
 * @param contractId the person chosen before, as the id of their contract
 *                   ({@link PaletteCommand#ACCEPT}); {@code null} otherwise
 */
public record PaletteSuggestionRequest(PaletteCommand command, PaletteParameter parameter, PaletteQuery query,
    LocalDate date, Long contractId) {

  public boolean is(PaletteCommand command, PaletteParameter parameter) {
    return this.command == command && this.parameter == parameter;
  }
}
