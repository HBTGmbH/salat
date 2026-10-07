package de.hbt.salat.dailyreport.controller;

import java.util.List;

/**
 * A section of the favourites list (#1414): the favourites of a group under its name, or — with
 * {@code groupName} {@code null} — those without a group, which stand first and need no heading.
 */
record FavoriteSectionView(String groupName, List<FavoriteView> favorites) {
}
