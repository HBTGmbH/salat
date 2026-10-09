package de.hbt.salat.dailyreport.domain;

import java.util.List;
import de.hbt.salat.favorites.domain.FavoriteEntry;

/**
 * The favourites the daily view offers in its card and in the dropdown „Favorit anwenden“ (#1414,
 * #1443), and how many there are in all — the link to the dialog names that number.
 *
 * <p>Only sections that hold a favourite: first the one without a group, which carries no heading,
 * then the groups in the person's order. Without groups, or ordered by use, there is a single
 * section without a heading.
 */
public record FavoriteShortList(List<Section> sections, int total) {

  public FavoriteShortList {
    sections = List.copyOf(sections);
  }

  /** A group, or — with {@code groupName} {@code null} — the favourites without one. */
  public record Section(String groupName, List<FavoriteEntry> favorites) {

    public Section {
      favorites = List.copyOf(favorites);
    }
  }

  public static FavoriteShortList none() {
    return new FavoriteShortList(List.of(), 0);
  }
}
