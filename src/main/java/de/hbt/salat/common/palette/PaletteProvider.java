package de.hbt.salat.common.palette;

import java.util.List;
import java.util.Map;

/**
 * What a module contributes to the object search of the command palette (#1157, ADR-0031). One
 * implementation per module, a Spring bean in that module; the module {@code palette} collects them
 * all — the pattern of {@code UiStateKeyContributor}.
 *
 * <p>Three contributions, all optional: the objects the module owns ({@link #search}), targets
 * for objects another module found ({@link #targetsFor}) — the order module finds an order, the
 * budget module adds its controlling —, and the values for the parameters of a command
 * ({@link #suggest}, #1158). The split follows the import rules: the module that finds an object
 * often may not import the one that decides about one of its targets.
 *
 * <p><b>Every provider decides itself what the current user may see</b>, per hit and per target: the
 * palette offers nothing whose page would answer with 403. A provider that cannot decide returns
 * nothing rather than throwing — an exception would take every other provider's hits along.
 */
public interface PaletteProvider {

  /** The objects of this module matching the query, each with the targets this module offers. */
  default List<PaletteHit> search(PaletteQuery query) {
    return List.of();
  }

  /**
   * Further targets for hits other providers found, by {@link PaletteHit#key()}. Only the hits that
   * may be shown are passed, a handful per kind.
   */
  default Map<String, List<PaletteTarget>> targetsFor(PaletteKind kind, List<PaletteHit> hits) {
    return Map.of();
  }

  /**
   * The values this module offers for a parameter of a command, best first (#1158). A module
   * answers for the commands whose page it owns: whoever protects the page knows which values it
   * would accept. Every other module returns nothing.
   */
  default List<PaletteSuggestion> suggest(PaletteSuggestionRequest request) {
    return List.of();
  }
}
