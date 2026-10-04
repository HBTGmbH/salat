package de.hbt.salat.common.palette;

/**
 * A value the palette offers for a parameter of a command (#1158).
 *
 * @param value           what goes into the target: an ISO date, a month {@code JJJJ-MM}, the id of an
 *                        order, a suborder or a contract
 * @param label           the business key as it is shown on the chip — never a database id
 * @param detail          what tells it apart from its neighbour; may be {@code null}
 * @param note            shown on the right, e.g. "Favorit" or why it cannot be chosen; may be
 *                        {@code null}
 * @param disabled        shown, but not to be chosen: a suborder the contract cannot book on the
 *                        chosen day
 * @param commentRequired a suborder that demands a comment
 * @param exact           the query is the whole key of the value — a person's sign, an order's sign —
 *                        so that a single word resolves it even where it begins other keys as well
 */
public record PaletteSuggestion(String value, String label, String detail, PaletteText note,
    boolean disabled, boolean commentRequired, boolean exact) {

  public static PaletteSuggestion of(String value, String label, String detail, PaletteText note) {
    return new PaletteSuggestion(value, label, detail, note, false, false, false);
  }
}
