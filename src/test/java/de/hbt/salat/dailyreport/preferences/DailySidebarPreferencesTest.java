package de.hbt.salat.dailyreport.preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import de.hbt.salat.common.beta.Betas;
import de.hbt.salat.dailyreport.service.DailyReportBetaFeatureContributor;
import de.hbt.salat.settings.service.UserPreferenceService;

/** The settings of the beta „Favoriten zuerst“ for the sidebar of the daily view (#1442). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class DailySidebarPreferencesTest {

  @Nested
  class Stored {

    @Test
    void the_beta_starts_with_the_favourites_above_the_week_in_the_own_order() {
      var preferences = DailySidebarPreferences.from(Map.of());

      assertThat(preferences).isEqualTo(new DailySidebarPreferences(
          WeekStripPlacement.BELOW_FAVORITES, FavoriteShortListOrder.OWN_ORDER));
      assertThat(preferences.isFavoritesFirst()).isTrue();
      assertThat(preferences.isWeekStripShown()).isTrue();
    }

    @Test
    void both_values_are_read() {
      var preferences = DailySidebarPreferences.from(Map.of("weekStrip", "hidden", "favoriteOrder", "recent"));

      assertThat(preferences).isEqualTo(new DailySidebarPreferences(
          WeekStripPlacement.HIDDEN, FavoriteShortListOrder.RECENTLY_USED));
      assertThat(preferences.isFavoritesFirst()).isFalse();
      assertThat(preferences.isWeekStripShown()).isFalse();
    }

    /** A value nobody wrote — or one of a version gone by — costs only itself, not the other value. */
    @Test
    void an_invalid_value_falls_back_to_its_default_alone() {
      var preferences = DailySidebarPreferences.from(Map.of("weekStrip", "sideways", "favoriteOrder", "recent"));

      assertThat(preferences).isEqualTo(new DailySidebarPreferences(
          WeekStripPlacement.BELOW_FAVORITES, FavoriteShortListOrder.RECENTLY_USED));
    }

    @Test
    void the_defaults_leave_nothing_behind() {
      assertThat(DailySidebarPreferences.defaults().toMap()).isEmpty();
      assertThat(new DailySidebarPreferences(WeekStripPlacement.ABOVE_FAVORITES, FavoriteShortListOrder.OWN_ORDER).toMap())
          .isEqualTo(Map.of("weekStrip", "above"));
    }

    @Test
    void what_is_written_is_read_back() {
      var preferences = new DailySidebarPreferences(WeekStripPlacement.HIDDEN, FavoriteShortListOrder.RECENTLY_USED);

      assertThat(DailySidebarPreferences.from(preferences.toMap())).isEqualTo(preferences);
    }

    @Test
    void the_sidebar_without_the_beta_is_the_one_of_6_5_0() {
      var classic = DailySidebarPreferences.classic();

      assertThat(classic.isFavoritesFirst()).isFalse();
      assertThat(classic.isWeekStripShown()).isTrue();
      assertThat(classic.favoriteOrder()).isEqualTo(FavoriteShortListOrder.RECENTLY_USED);
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class Effective {

    @Mock
    private UserPreferenceService userPreferenceService;
    @Mock
    private Betas betas;
    @InjectMocks
    private DailySidebarPreferenceService service;

    @Test
    void with_the_beta_on_the_daily_view_follows_the_choice() {
      when(betas.isEnabled(DailyReportBetaFeatureContributor.FAVORITES_FIRST)).thenReturn(true);
      when(userPreferenceService.getModuleSettings(DailySidebarPreferences.MODULE_KEY))
          .thenReturn(Map.of("weekStrip", "hidden"));

      assertThat(service.getEffectiveForCurrentUser().weekStrip()).isEqualTo(WeekStripPlacement.HIDDEN);
    }

    /** Switched off, the stored choice stays, but nothing of it shows. */
    @Test
    void with_the_beta_off_the_daily_view_is_the_classic_one_whatever_was_stored() {
      when(betas.isEnabled(DailyReportBetaFeatureContributor.FAVORITES_FIRST)).thenReturn(false);

      assertThat(service.getEffectiveForCurrentUser()).isEqualTo(DailySidebarPreferences.classic());
      verify(userPreferenceService, never()).getModuleSettings(DailySidebarPreferences.MODULE_KEY);
    }

    @Test
    void the_choice_is_stored_under_its_own_key() {
      service.saveForCurrentUser(new DailySidebarPreferences(WeekStripPlacement.HIDDEN, FavoriteShortListOrder.OWN_ORDER));

      verify(userPreferenceService).saveModuleSettings(DailySidebarPreferences.MODULE_KEY, Map.of("weekStrip", "hidden"));
    }
  }
}
