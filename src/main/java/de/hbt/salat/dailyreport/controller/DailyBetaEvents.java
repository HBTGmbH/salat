package de.hbt.salat.dailyreport.controller;

/**
 * The uses of the daily report's betas that are counted (#1447), with the beta on and, for
 * comparison, off. Both happen without the beta as well; that is what makes them comparable.
 */
public final class DailyBetaEvents {

  /** A favourite applied on the daily view, from the card, the dropdown or the dialog (#1442). */
  public static final String FAVORITE_APPLIED = "favorite-applied";

  /**
   * The dialog with all favourites opened from the daily view (#1443). Counted in the browser:
   * {@code data-beta-usage="favoritesfirst:favorite-dialog-opened"} at its opener.
   */
  public static final String FAVORITE_DIALOG_OPENED = "favorite-dialog-opened";

  private DailyBetaEvents() {
  }
}
