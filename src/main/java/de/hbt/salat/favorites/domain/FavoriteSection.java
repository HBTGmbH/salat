package de.hbt.salat.favorites.domain;

import java.util.List;

/**
 * One section of a person's favourites (#1414): a group, or — with {@code groupId} {@code null} —
 * the favourites without one. The favourites stand in display order.
 */
public record FavoriteSection(Long groupId, String groupName, List<FavoriteEntry> favorites) {

  public FavoriteSection {
    favorites = List.copyOf(favorites);
  }

  public boolean isUngrouped() {
    return groupId == null;
  }
}
