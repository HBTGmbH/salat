package de.hbt.salat.common.palette;

import java.util.List;

/**
 * A business object the palette offers, with the targets it leads to (#1157).
 *
 * @param kind     the group the hit belongs to
 * @param key      identifies the object within its kind, so that other modules can add targets to it
 *                 ({@link PaletteProvider#targetsFor}); not shown
 * @param title    the business key as it is shown: an order's sign, a person's name and sign — never
 *                 a database id
 * @param subtitle what tells it apart from its neighbour: description, customer; may be {@code null}
 * @param context  shown on the right, e.g. the order of a suborder or the period of a contract; may be
 *                 {@code null}
 * @param ended    the validity lies in the past (ADR-0029); ranked lower and marked
 * @param hidden   hidden from selection lists (ADR-0012); ranked lower and marked
 * @param match    how well the query matches, higher is better ({@link PaletteQuery#match})
 * @param targets  where the hit leads; a hit without any target is not offered
 */
public record PaletteHit(PaletteKind kind, String key, String title, String subtitle,
    PaletteText context, boolean ended, boolean hidden, int match, List<PaletteTarget> targets) {

  public PaletteHit {
    targets = List.copyOf(targets);
  }

  public PaletteHit withTargets(List<PaletteTarget> targets) {
    return new PaletteHit(kind, key, title, subtitle, context, ended, hidden, match, targets);
  }
}
