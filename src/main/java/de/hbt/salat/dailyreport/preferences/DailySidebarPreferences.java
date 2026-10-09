package de.hbt.salat.dailyreport.preferences;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The settings of the beta „Favoriten zuerst“ for the sidebar of the daily view (#1442, #1443),
 * stored under {@link #MODULE_KEY}. They apply only while the beta is on; switched off, the view is
 * the one of 6.5.0 ({@link #classic()}) and the stored values stay.
 *
 * @param weekStrip     where the card „Diese Woche“ stands
 * @param favoriteOrder what the favourites card and the dropdown follow
 */
public record DailySidebarPreferences(WeekStripPlacement weekStrip, FavoriteShortListOrder favoriteOrder) {

  public static final String MODULE_KEY = "dailySidebar";
  static final String KEY_WEEK_STRIP = "weekStrip";
  static final String KEY_FAVORITE_ORDER = "favoriteOrder";

  /** What the beta starts with until the person chooses. */
  public static DailySidebarPreferences defaults() {
    return new DailySidebarPreferences(WeekStripPlacement.BELOW_FAVORITES, FavoriteShortListOrder.OWN_ORDER);
  }

  /** The sidebar without the beta: the week above the favourites, these used last. */
  public static DailySidebarPreferences classic() {
    return new DailySidebarPreferences(WeekStripPlacement.ABOVE_FAVORITES, FavoriteShortListOrder.RECENTLY_USED);
  }

  /** Every key is read on its own: a missing or unknown value falls back to its default, not the other. */
  public static DailySidebarPreferences from(Map<String, Object> map) {
    var weekStrip = map.get(KEY_WEEK_STRIP) == null ? null
        : WeekStripPlacement.ofKey(map.get(KEY_WEEK_STRIP).toString()).orElse(null);
    var favoriteOrder = map.get(KEY_FAVORITE_ORDER) == null ? null
        : FavoriteShortListOrder.ofKey(map.get(KEY_FAVORITE_ORDER).toString()).orElse(null);
    return new DailySidebarPreferences(
        weekStrip != null ? weekStrip : defaults().weekStrip(),
        favoriteOrder != null ? favoriteOrder : defaults().favoriteOrder());
  }

  /**
   * From the keys the settings form sends; one it does not know falls back to the default — the
   * form offers no other, so only a forged request sends one.
   */
  public static DailySidebarPreferences ofKeys(String weekStripKey, String favoriteOrderKey) {
    var values = new LinkedHashMap<String, Object>();
    if (weekStripKey != null) {
      values.put(KEY_WEEK_STRIP, weekStripKey);
    }
    if (favoriteOrderKey != null) {
      values.put(KEY_FAVORITE_ORDER, favoriteOrderKey);
    }
    return from(values);
  }

  /** Defaults are left out, so that going back to one leaves nothing behind. */
  public Map<String, Object> toMap() {
    var values = new LinkedHashMap<String, Object>();
    if (weekStrip != defaults().weekStrip()) {
      values.put(KEY_WEEK_STRIP, weekStrip.getKey());
    }
    if (favoriteOrder != defaults().favoriteOrder()) {
      values.put(KEY_FAVORITE_ORDER, favoriteOrder.getKey());
    }
    return Map.copyOf(values);
  }

  /** Whether the favourites stand above the week. */
  public boolean isFavoritesFirst() {
    return weekStrip == WeekStripPlacement.BELOW_FAVORITES;
  }

  public boolean isWeekStripShown() {
    return weekStrip != WeekStripPlacement.HIDDEN;
  }
}
