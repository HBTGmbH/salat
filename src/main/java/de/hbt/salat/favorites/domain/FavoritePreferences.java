package de.hbt.salat.favorites.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How a person wants their favourites shown (#1414), stored in the preference store under
 * {@link #MODULE_KEY}.
 *
 * @param sortOrder the order within the groups
 * @param listSize  how many favourites the short lists of the booking pages show, the ones used last
 */
public record FavoritePreferences(FavoriteSortOrder sortOrder, int listSize) {

  public static final String MODULE_KEY = "favorites";
  public static final int DEFAULT_LIST_SIZE = 10;
  public static final int MIN_LIST_SIZE = 1;
  public static final int MAX_LIST_SIZE = 50;

  static final String KEY_SORT_ORDER = "sortOrder";
  static final String KEY_LIST_SIZE = "listSize";

  public static FavoritePreferences defaults() {
    return new FavoritePreferences(FavoriteSortOrder.RECENT, DEFAULT_LIST_SIZE);
  }

  public static boolean isValidListSize(int listSize) {
    return listSize >= MIN_LIST_SIZE && listSize <= MAX_LIST_SIZE;
  }

  /** Every key is read on its own: a malformed value falls back to its default, not the others. */
  public static FavoritePreferences from(Map<String, Object> map) {
    var sortOrder = map.get(KEY_SORT_ORDER) == null ? FavoriteSortOrder.RECENT
        : FavoriteSortOrder.ofKey(map.get(KEY_SORT_ORDER).toString()).orElse(FavoriteSortOrder.RECENT);
    return new FavoritePreferences(sortOrder, listSizeOf(map.get(KEY_LIST_SIZE)));
  }

  /** Defaults are left out, so that going back to one leaves nothing behind. */
  public Map<String, Object> toMap() {
    var values = new LinkedHashMap<String, Object>();
    if (sortOrder != FavoriteSortOrder.RECENT) {
      values.put(KEY_SORT_ORDER, sortOrder.getKey());
    }
    if (listSize != DEFAULT_LIST_SIZE) {
      values.put(KEY_LIST_SIZE, Integer.toString(listSize));
    }
    return Map.copyOf(values);
  }

  public FavoritePreferences withSortOrder(FavoriteSortOrder newSortOrder) {
    return new FavoritePreferences(newSortOrder, listSize);
  }

  public FavoritePreferences withListSize(int newListSize) {
    return new FavoritePreferences(sortOrder, newListSize);
  }

  private static int listSizeOf(Object value) {
    try {
      int listSize = value == null ? DEFAULT_LIST_SIZE : Integer.parseInt(value.toString());
      return isValidListSize(listSize) ? listSize : DEFAULT_LIST_SIZE;
    } catch (NumberFormatException e) {
      return DEFAULT_LIST_SIZE;
    }
  }
}
