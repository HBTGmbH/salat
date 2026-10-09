package de.hbt.salat.dailyreport.preferences;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Where the card „Diese Woche“ stands in the sidebar of the daily view (#1442). */
@Getter
@RequiredArgsConstructor
public enum WeekStripPlacement {

  /** Above the favourites, as without the beta. */
  ABOVE_FAVORITES("above"),
  /** Below the favourites — the default of the beta. */
  BELOW_FAVORITES("below"),
  /** Not at all. */
  HIDDEN("hidden");

  private final String key;

  public static Optional<WeekStripPlacement> ofKey(String key) {
    return Arrays.stream(values()).filter(placement -> placement.key.equals(key)).findFirst();
  }
}
