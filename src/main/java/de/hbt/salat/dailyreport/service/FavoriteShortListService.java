package de.hbt.salat.dailyreport.service;

import static de.hbt.salat.dailyreport.service.DailyReportBetaFeatureContributor.FAVORITES_FIRST;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.beta.Betas;
import de.hbt.salat.dailyreport.domain.FavoriteShortList;
import de.hbt.salat.dailyreport.preferences.DailySidebarPreferenceService;
import de.hbt.salat.dailyreport.preferences.FavoriteShortListOrder;
import de.hbt.salat.favorites.domain.FavoritePreferences;
import de.hbt.salat.favorites.service.FavoriteService;

/**
 * What the favourites card and the dropdown „Favorit anwenden“ of the daily view offer (#1414,
 * #1443) — both the same favourites in the same order with the same headings.
 *
 * <p>Without the beta „Favoriten zuerst“ the favourites used last, as many as by default, whatever
 * the person chose. With it, either the arrangement of the dialog „Favoriten“ with its groups and
 * all favourites, or the favourites used last, as many as chosen.
 */
@Service
@RequiredArgsConstructor
@Authorized
public class FavoriteShortListService {

  private final FavoriteService favoriteService;
  private final DailySidebarPreferenceService sidebarPreferenceService;
  private final Betas betas;

  /** The favourites of the logged-in person as the daily view offers them. */
  public FavoriteShortList getForCurrentUser() {
    if (!betas.isEnabled(FAVORITES_FIRST)) {
      return recentlyUsed(FavoritePreferences.DEFAULT_LIST_SIZE);
    }
    var order = sidebarPreferenceService.getForCurrentUser().favoriteOrder();
    return order == FavoriteShortListOrder.OWN_ORDER ? inOwnOrder() : recentlyUsed(favoriteService.getListSize());
  }

  private FavoriteShortList recentlyUsed(int limit) {
    var recent = favoriteService.getRecentFavorites(limit);
    if (recent.favorites().isEmpty()) {
      return new FavoriteShortList(List.of(), recent.total());
    }
    return new FavoriteShortList(List.of(new FavoriteShortList.Section(null, recent.favorites())), recent.total());
  }

  /** The sections of the dialog that hold a favourite; the one without a group keeps no heading. */
  private FavoriteShortList inOwnOrder() {
    var list = favoriteService.getOwnFavoriteList();
    var sections = list.sections().stream()
        .filter(section -> !section.favorites().isEmpty())
        .map(section -> new FavoriteShortList.Section(section.groupName(), section.favorites()))
        .toList();
    return new FavoriteShortList(sections, list.favorites().size());
  }
}
