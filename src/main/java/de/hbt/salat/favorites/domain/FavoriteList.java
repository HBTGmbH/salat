package de.hbt.salat.favorites.domain;

import java.util.List;

/**
 * The favourites of a person as the lists show them (#1414): first those without a group, then the
 * groups in the person's order, each in the chosen {@link FavoriteSortOrder}. The section without a
 * group always comes first, and every group is listed, empty ones included — the dialog that arranges
 * them needs them as drop targets; a list that only offers favourites leaves them out.
 */
public record FavoriteList(FavoriteSortOrder sortOrder, List<FavoriteSection> sections) {

  public FavoriteList {
    sections = List.copyOf(sections);
  }

  /** All favourites in display order. */
  public List<FavoriteEntry> favorites() {
    return sections.stream().flatMap(section -> section.favorites().stream()).toList();
  }

  public boolean isEmpty() {
    return sections.stream().allMatch(section -> section.favorites().isEmpty());
  }

  /** Whether the person has any group at all. */
  public boolean hasGroups() {
    return sections.stream().anyMatch(section -> !section.isUngrouped());
  }
}
