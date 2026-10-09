package de.hbt.salat.common.beta;

import java.util.List;

/**
 * Contributes the betas of a module (#1447) — the pattern of {@code UiStateKeyContributor}
 * (ADR-0016) and {@code PaletteProvider}. Implement it in the module whose page carries the beta,
 * annotate the implementation with {@code @Component} and keep a public no-argument constructor:
 * the check that every beta has its texts builds the contributors without Spring.
 *
 * <p>Example (dailyreport module):
 * <pre>{@code
 * @Component
 * public class DailyReportBetaFeatureContributor implements BetaFeatureContributor {
 *     public static final BetaFeature FAVORITES_FIRST = new BetaFeature("favoritesfirst", 20);
 *
 *     @Override
 *     public List<BetaFeature> getBetaFeatures() {
 *         return List.of(FAVORITES_FIRST);
 *     }
 * }
 * }</pre>
 */
public interface BetaFeatureContributor {

  List<BetaFeature> getBetaFeatures();
}
