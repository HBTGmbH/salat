package de.hbt.salat.dailyreport.viewhelper;

import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import de.hbt.salat.dailyreport.preferences.DailySidebarPreferenceService;
import de.hbt.salat.dailyreport.preferences.DailySidebarPreferences;

/**
 * The arrangement of the sidebar of the daily view (#1442), for the templates:
 * {@code @dailySidebarViewHelper.favoritesFirst}. A view helper rather than a model attribute for
 * the reason {@code BetaViewHelper} gives — the sidebar and the out-of-band swap of the week are
 * rendered from several endpoints, and a model attribute would be missing in the next one.
 *
 * <p>Request scoped, so a page with its fragments reads the preferences once.
 */
@Slf4j
@Component
@Scope(value = SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class DailySidebarViewHelper {

  private final DailySidebarPreferenceService sidebarPreferenceService;

  private DailySidebarPreferences resolved;

  /** Whether the favourites stand above the week. */
  public boolean isFavoritesFirst() {
    return effective().isFavoritesFirst();
  }

  /** Whether the card „Diese Woche“ is there at all; without it there is no out-of-band swap of it either. */
  public boolean isWeekStripShown() {
    return effective().isWeekStripShown();
  }

  private DailySidebarPreferences effective() {
    if (resolved == null) {
      try {
        resolved = sidebarPreferenceService.getEffectiveForCurrentUser();
      } catch (RuntimeException e) {
        // the arrangement of a sidebar must never be the reason a page fails to render
        log.debug("Could not read the sidebar preferences, falling back to the classic sidebar", e);
        resolved = DailySidebarPreferences.classic();
      }
    }
    return resolved;
  }
}
