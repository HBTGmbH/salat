package de.hbt.salat.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static de.hbt.salat.dailyreport.service.DailyReportBetaFeatureContributor.FAVORITES_FIRST;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import de.hbt.salat.common.beta.Betas;
import de.hbt.salat.dailyreport.domain.FavoriteShortList;
import de.hbt.salat.dailyreport.preferences.DailySidebarPreferenceService;
import de.hbt.salat.dailyreport.preferences.DailySidebarPreferences;
import de.hbt.salat.dailyreport.preferences.FavoriteShortListOrder;
import de.hbt.salat.dailyreport.preferences.WeekStripPlacement;
import de.hbt.salat.favorites.domain.FavoriteEntry;
import de.hbt.salat.favorites.domain.FavoriteList;
import de.hbt.salat.favorites.domain.FavoriteSection;
import de.hbt.salat.favorites.domain.FavoriteSortOrder;
import de.hbt.salat.favorites.domain.RecentFavorites;
import de.hbt.salat.favorites.service.FavoriteService;

/**
 * What the favourites card and the dropdown of the daily view offer (#1443), for both values of the
 * beta switch „Favoriten zuerst“ (#1442) and, with it on, for both orders.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class FavoriteShortListServiceTest {

  private static final FavoriteEntry ROUTINE = favourite(1L, null, null);
  private static final FavoriteEntry DAILY = favourite(2L, 10L, "Kunde A");
  private static final FavoriteEntry REVIEW = favourite(3L, 10L, "Kunde A");
  private static final FavoriteEntry PATCH = favourite(4L, 30L, "Wartung");

  /** The dialog's arrangement: ungrouped first, then the groups in their order, an empty one among them. */
  private static final FavoriteList OWN_ARRANGEMENT = new FavoriteList(FavoriteSortOrder.CUSTOM, List.of(
      new FavoriteSection(null, null, List.of(ROUTINE)),
      new FavoriteSection(10L, "Kunde A", List.of(DAILY, REVIEW)),
      new FavoriteSection(20L, "Leer", List.of()),
      new FavoriteSection(30L, "Wartung", List.of(PATCH))));

  @Mock
  private FavoriteService favoriteService;
  @Mock
  private DailySidebarPreferenceService sidebarPreferenceService;
  @Mock
  private Betas betas;
  @InjectMocks
  private FavoriteShortListService service;

  @Nested
  class WithoutTheBeta {

    /** As in 6.5.0: the number a person chose applies only within the beta. */
    @Test
    void the_favourites_used_last_in_the_default_number_without_headings() {
      when(betas.isEnabled(FAVORITES_FIRST)).thenReturn(false);
      when(favoriteService.getListSize()).thenReturn(3);
      when(favoriteService.getRecentFavorites(10)).thenReturn(new RecentFavorites(List.of(PATCH, DAILY), 4));

      var shortList = service.getForCurrentUser();

      assertThat(shortList.sections()).singleElement().satisfies(section -> {
        assertThat(section.groupName()).isNull();
        assertThat(section.favorites()).containsExactly(PATCH, DAILY);
      });
      assertThat(shortList.total()).isEqualTo(4);
    }

    /** A choice stored while the beta was on does not show once it is off. */
    @Test
    void a_stored_own_order_does_not_apply() {
      when(betas.isEnabled(FAVORITES_FIRST)).thenReturn(false);
      givenOrder(FavoriteShortListOrder.OWN_ORDER);
      when(favoriteService.getRecentFavorites(10)).thenReturn(new RecentFavorites(List.of(PATCH), 4));

      service.getForCurrentUser();

      verify(favoriteService, never()).getOwnFavoriteList();
    }

    @Test
    void without_favourites_there_is_no_section() {
      when(betas.isEnabled(FAVORITES_FIRST)).thenReturn(false);
      when(favoriteService.getRecentFavorites(10)).thenReturn(new RecentFavorites(List.of(), 0));

      assertThat(service.getForCurrentUser()).isEqualTo(FavoriteShortList.none());
    }
  }

  @Nested
  class WithTheBetaInTheOwnOrder {

    @Test
    void the_favourites_stand_as_in_the_dialog_ungrouped_first_and_without_a_heading() {
      when(betas.isEnabled(FAVORITES_FIRST)).thenReturn(true);
      givenOrder(FavoriteShortListOrder.OWN_ORDER);
      when(favoriteService.getOwnFavoriteList()).thenReturn(OWN_ARRANGEMENT);

      var shortList = service.getForCurrentUser();

      assertThat(shortList.sections()).extracting(FavoriteShortList.Section::groupName)
          .containsExactly(null, "Kunde A", "Wartung");
      assertThat(shortList.sections()).flatExtracting(FavoriteShortList.Section::favorites)
          .containsExactly(ROUTINE, DAILY, REVIEW, PATCH);
    }

    /** All of them: the number of the short list applies only to the list ordered by use. */
    @Test
    void every_favourite_is_offered_whatever_number_was_chosen() {
      when(betas.isEnabled(FAVORITES_FIRST)).thenReturn(true);
      givenOrder(FavoriteShortListOrder.OWN_ORDER);
      when(favoriteService.getListSize()).thenReturn(1);
      when(favoriteService.getOwnFavoriteList()).thenReturn(OWN_ARRANGEMENT);

      var shortList = service.getForCurrentUser();

      assertThat(shortList.sections()).flatExtracting(FavoriteShortList.Section::favorites).hasSize(4);
      assertThat(shortList.total()).isEqualTo(4);
      verify(favoriteService, never()).getRecentFavorites(1);
    }

    @Test
    void without_groups_there_is_no_heading() {
      when(betas.isEnabled(FAVORITES_FIRST)).thenReturn(true);
      givenOrder(FavoriteShortListOrder.OWN_ORDER);
      when(favoriteService.getOwnFavoriteList()).thenReturn(new FavoriteList(FavoriteSortOrder.CUSTOM,
          List.of(new FavoriteSection(null, null, List.of(ROUTINE, PATCH)))));

      var shortList = service.getForCurrentUser();

      assertThat(shortList.sections()).singleElement().satisfies(section -> {
        assertThat(section.groupName()).isNull();
        assertThat(section.favorites()).containsExactly(ROUTINE, PATCH);
      });
    }

    /** With every favourite in a group, the first heading is the first group's. */
    @Test
    void an_empty_section_without_a_group_is_left_out_as_well() {
      when(betas.isEnabled(FAVORITES_FIRST)).thenReturn(true);
      givenOrder(FavoriteShortListOrder.OWN_ORDER);
      when(favoriteService.getOwnFavoriteList()).thenReturn(new FavoriteList(FavoriteSortOrder.RECENT, List.of(
          new FavoriteSection(null, null, List.of()),
          new FavoriteSection(30L, "Wartung", List.of(PATCH)))));

      assertThat(service.getForCurrentUser().sections()).extracting(FavoriteShortList.Section::groupName)
          .containsExactly("Wartung");
    }
  }

  @Nested
  class WithTheBetaOrderedByUse {

    @Test
    void the_favourites_used_last_in_the_chosen_number_without_headings() {
      when(betas.isEnabled(FAVORITES_FIRST)).thenReturn(true);
      givenOrder(FavoriteShortListOrder.RECENTLY_USED);
      when(favoriteService.getListSize()).thenReturn(2);
      when(favoriteService.getRecentFavorites(2)).thenReturn(new RecentFavorites(List.of(PATCH, DAILY), 4));

      var shortList = service.getForCurrentUser();

      assertThat(shortList.sections()).singleElement().satisfies(section -> {
        assertThat(section.groupName()).isNull();
        assertThat(section.favorites()).containsExactly(PATCH, DAILY);
      });
      assertThat(shortList.total()).isEqualTo(4);
      verify(favoriteService, never()).getOwnFavoriteList();
    }
  }

  private void givenOrder(FavoriteShortListOrder order) {
    when(sidebarPreferenceService.getForCurrentUser())
        .thenReturn(new DailySidebarPreferences(WeekStripPlacement.BELOW_FAVORITES, order));
  }

  private static FavoriteEntry favourite(long id, Long groupId, String groupName) {
    return new FavoriteEntry(id, 7L, "4711.01 - Konzept", 1, 0, "Favorit " + id, List.of(), groupId, groupName, null);
  }
}
