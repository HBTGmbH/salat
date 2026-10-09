package de.hbt.salat.dailyreport.controller;

import java.util.List;

/**
 * A section of the favourites card and the dropdown (#1443): a group under its name, or — with
 * {@code groupName} {@code null} — favourites without a heading.
 */
record FavoriteViewSection(String groupName, List<FavoriteView> favorites) {
}
