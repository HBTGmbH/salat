package de.hbt.salat.favorites.domain;

import java.util.List;

/**
 * The short list of the booking pages (#1414): the favourites used last, at most as many as the
 * person chose, and how many there are in all — the link to the full list names that number.
 */
public record RecentFavorites(List<FavoriteEntry> favorites, int total) {

  public RecentFavorites {
    favorites = List.copyOf(favorites);
  }

  public boolean isEmpty() {
    return total == 0;
  }
}
