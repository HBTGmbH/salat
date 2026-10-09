package de.hbt.salat.dailyreport.service;

import java.util.List;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.beta.BetaFeature;
import de.hbt.salat.common.beta.BetaFeatureContributor;

/**
 * The betas of the daily report (#1447): it declares them, asks and counts through
 * {@code Betas}; how a beta is introduced, measured and ended is in {@code docs/beta-funktionen.md}.
 */
@Component
public class DailyReportBetaFeatureContributor implements BetaFeatureContributor {

  /**
   * „Favoriten zuerst“ (#1442, #1443): in the sidebar of the daily view the favourites stand above
   * the week, and card and dropdown follow the person's own order with its groups. Its settings are
   * {@code DailySidebarPreferences} and the size of the short list; without the beta neither applies.
   * Asked about after 20 favourites applied with it on.
   */
  public static final BetaFeature FAVORITES_FIRST = new BetaFeature("favoritesfirst", 20);

  @Override
  public List<BetaFeature> getBetaFeatures() {
    return List.of(FAVORITES_FIRST);
  }
}
