package de.hbt.salat.dailyreport.preferences;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * What the favourites card and the dropdown „Favorit anwenden“ of the daily view follow (#1443).
 * Not the order within the groups: that one is chosen in the dialog „Favoriten“
 * ({@code FavoriteSortOrder}) and the own order takes it over.
 */
@Getter
@RequiredArgsConstructor
public enum FavoriteShortListOrder {

  /** The arrangement of the dialog, with its groups and all favourites — the default of the beta. */
  OWN_ORDER("own"),
  /** The favourites used last, without groups, as many as chosen — as without the beta. */
  RECENTLY_USED("recent");

  private final String key;

  public static Optional<FavoriteShortListOrder> ofKey(String key) {
    return Arrays.stream(values()).filter(order -> order.key.equals(key)).findFirst();
  }
}
