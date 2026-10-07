package de.hbt.salat.favorites.domain;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * How the favourites within a group are ordered (#1414). The groups themselves always stand in the
 * order the person gave them.
 */
@Getter
@RequiredArgsConstructor
public enum FavoriteSortOrder {

  /** The favourite applied last first — the default. */
  RECENT("recent"),
  /** The order the person arranged by drag and drop or with the arrow buttons. */
  CUSTOM("custom");

  private final String key;

  public static Optional<FavoriteSortOrder> ofKey(String key) {
    return Arrays.stream(values()).filter(order -> order.key.equals(key)).findFirst();
  }
}
