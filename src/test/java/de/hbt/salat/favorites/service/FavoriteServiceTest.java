package de.hbt.salat.favorites.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static de.hbt.salat.favorites.domain.FavoriteSortOrder.CUSTOM;
import static de.hbt.salat.favorites.domain.FavoriteSortOrder.RECENT;
import static de.hbt.salat.testutils.ReferenceTestUtils.employeeWithId;
import static de.hbt.salat.testutils.ReferenceTestUtils.employeeorderWithId;
import static de.hbt.salat.testutils.ReferenceTestUtils.suborderWithId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.favorites.domain.Favorite;
import de.hbt.salat.favorites.domain.FavoriteEntry;
import de.hbt.salat.favorites.domain.FavoriteGroup;
import de.hbt.salat.favorites.domain.FavoriteLayout;
import de.hbt.salat.favorites.domain.FavoritePreferences;
import de.hbt.salat.favorites.domain.FavoriteSection;
import de.hbt.salat.favorites.domain.NewFavorite;
import de.hbt.salat.favorites.persistence.EmployeeReferences;
import de.hbt.salat.favorites.persistence.EmployeeorderReferences;
import de.hbt.salat.favorites.persistence.FavoriteGroupRepository;
import de.hbt.salat.favorites.persistence.FavoriteRepository;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.service.EmployeeorderService;
import de.hbt.salat.settings.service.UserPreferenceService;

/**
 * A favourite has no person of its own: it belongs to the person of its employee order (#1369).
 * Whose an employee order is, the order module answers. Its groups belong to the person they were
 * created for (#1414), and nobody else reads, changes or fills them.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class FavoriteServiceTest {

  private static final long ME = 1L;
  private static final long SOMEBODY_ELSE = 2L;
  private static final long MY_ORDER = 10L;
  private static final long FOREIGN_ORDER = 20L;
  private static final LocalDateTime MONDAY = LocalDateTime.parse("2026-10-05T09:00");

  @Mock
  private FavoriteRepository favoriteRepository;
  @Mock
  private FavoriteGroupRepository favoriteGroupRepository;
  @Mock
  private AuthorizedEmployee authorizedEmployee;
  @Mock
  private EmployeeorderReferences employeeorderReferences;
  @Mock
  private EmployeeReferences employeeReferences;
  @Mock
  private EmployeeorderService employeeorderService;
  @Mock
  private UserPreferenceService userPreferenceService;

  @InjectMocks
  private FavoriteService favoriteService;

  /** What the preference store holds for the login, by module. */
  private final Map<String, Map<String, Object>> preferences = new HashMap<>();

  @BeforeEach
  void setUp() {
    when(authorizedEmployee.getEmployeeId()).thenReturn(ME);
    when(employeeorderService.getEmployeeIdOfEmployeeorder(MY_ORDER)).thenReturn(Optional.of(ME));
    when(employeeorderService.getEmployeeIdOfEmployeeorder(FOREIGN_ORDER)).thenReturn(Optional.of(SOMEBODY_ELSE));
    when(employeeorderReferences.employeeorder(any(Long.class)))
        .thenAnswer(invocation -> employeeorderWithId(invocation.<Long>getArgument(0)));
    when(employeeReferences.employee(anyLong()))
        .thenAnswer(invocation -> employeeWithId(invocation.<Long>getArgument(0)));
    when(favoriteRepository.saveAndFlush(any())).thenAnswer(invocation -> {
      Favorite favorite = invocation.getArgument(0);
      setField(favorite, "id", 99L);
      return favorite;
    });
    when(favoriteGroupRepository.saveAndFlush(any())).thenAnswer(invocation -> {
      FavoriteGroup group = invocation.getArgument(0);
      setField(group, "id", 77L);
      return group;
    });
    when(userPreferenceService.getModuleSettings(anyString()))
        .thenAnswer(invocation -> preferences.getOrDefault(invocation.<String>getArgument(0), Map.of()));
  }

  @Nested
  class Adding {

    @Test
    void a_favourite_on_an_own_employee_order_refers_to_it() {
      var id = favoriteService.addFavorite(new NewFavorite(MY_ORDER, 1, 30, "Daily", List.of("abc-1")));

      var saved = savedFavorite();
      assertThat(id).isEqualTo(99L);
      assertThat(saved.getEmployeeorderId()).isEqualTo(MY_ORDER);
      assertThat(saved.getTicketReferences()).containsExactly("ABC-1");
    }

    /** Otherwise the favourite would appear in the list of the other person. */
    @Test
    void a_favourite_on_the_employee_order_of_somebody_else_is_refused() {
      assertThatThrownBy(() -> favoriteService.addFavorite(new NewFavorite(FOREIGN_ORDER, 1, 0, null, null)))
          .isInstanceOf(AuthorizationException.class);

      verify(favoriteRepository, never()).saveAndFlush(any());
    }

    @Test
    void an_unknown_employee_order_is_refused() {
      assertThatThrownBy(() -> favoriteService.addFavorite(new NewFavorite(404L, 1, 0, null, null)))
          .isInstanceOf(AuthorizationException.class);
    }

    /** In both orders a new favourite is the first one the person sees (#1414). */
    @Test
    void a_new_favourite_counts_as_just_used_and_stands_on_top_of_those_without_a_group() {
      when(favoriteRepository.findMinPositionWithoutGroup(ME)).thenReturn(0);

      favoriteService.addFavorite(new NewFavorite(MY_ORDER, 1, 0, "Daily", null));

      var saved = savedFavorite();
      assertThat(saved.getGroup()).isNull();
      assertThat(saved.getPosition()).isEqualTo(-1);
      assertThat(saved.getLastUsed()).isNotNull();
    }

    @Test
    void a_new_favourite_can_be_sorted_into_an_own_group_at_its_top() {
      givenGroups(group(5L, ME, "Wartung", 0));
      when(favoriteRepository.findMinPositionInGroup(5L)).thenReturn(null);

      favoriteService.addFavorite(new NewFavorite(MY_ORDER, 1, 0, "Daily", null, 5L));

      var saved = savedFavorite();
      assertThat(saved.getGroupId()).isEqualTo(5L);
      assertThat(saved.getPosition()).isZero();
    }

    @Test
    void a_new_favourite_is_not_sorted_into_the_group_of_somebody_else() {
      givenGroups(group(6L, SOMEBODY_ELSE, "Fremd", 0));

      assertThatThrownBy(() -> favoriteService.addFavorite(new NewFavorite(MY_ORDER, 1, 0, "Daily", null, 6L)))
          .isInstanceOf(AuthorizationException.class);

      verify(favoriteRepository, never()).saveAndFlush(any());
    }

    private Favorite savedFavorite() {
      var saved = ArgumentCaptor.forClass(Favorite.class);
      verify(favoriteRepository).saveAndFlush(saved.capture());
      return saved.getValue();
    }
  }

  @Nested
  class Reading {

    /** Applied last first; never applied behind, the one created last first among them. */
    @Test
    void ordered_by_use_the_one_applied_last_comes_first() {
      var older = favorite(1L, null, null, MONDAY);
      var newer = favorite(2L, null, null, MONDAY.plusDays(1));
      var neverUsedOld = favorite(3L, null, null, null);
      var neverUsedNew = favorite(4L, null, null, null);
      givenFavorites(older, newer, neverUsedOld, neverUsedNew);

      var list = favoriteService.getOwnFavoriteList();

      assertThat(list.sortOrder()).isEqualTo(RECENT);
      assertThat(ids(list.sections().getFirst())).containsExactly(2L, 1L, 4L, 3L);
    }

    @Test
    void the_favourites_without_a_group_come_first_then_every_group_in_its_order() {
      var maintenance = group(5L, ME, "Wartung", 1);
      var project = group(6L, ME, "Projekt", 0);
      var empty = group(7L, ME, "Leer", 2);
      givenGroups(project, maintenance, empty);
      givenFavorites(favorite(1L, maintenance, null, MONDAY), favorite(2L, null, null, MONDAY),
          favorite(3L, project, null, MONDAY));

      var sections = favoriteService.getOwnFavoriteList().sections();

      assertThat(sections).extracting(FavoriteSection::groupName).containsExactly(null, "Projekt", "Wartung", "Leer");
      assertThat(sections).extracting(SectionIds::of).containsExactly(List.of(2L), List.of(3L), List.of(1L), List.of());
    }

    /** Placed by hand first, the rest behind them by use. */
    @Test
    void in_the_own_order_the_places_count_and_unplaced_favourites_follow() {
      preferences.put(FavoritePreferences.MODULE_KEY, Map.of("sortOrder", "custom"));
      givenFavorites(favorite(1L, null, 1, MONDAY), favorite(2L, null, 0, null),
          favorite(3L, null, null, MONDAY.plusDays(2)), favorite(4L, null, null, MONDAY));

      var list = favoriteService.getOwnFavoriteList();

      assertThat(list.sortOrder()).isEqualTo(CUSTOM);
      assertThat(ids(list.sections().getFirst())).containsExactly(2L, 1L, 3L, 4L);
    }

    @Test
    void an_entry_carries_its_suborder_and_group_as_values() {
      var maintenance = group(5L, ME, "Wartung", 0);
      givenGroups(maintenance);
      givenFavorites(favorite(1L, maintenance, null, MONDAY));

      FavoriteEntry entry = favoriteService.getOwnFavoriteList().favorites().getFirst();

      assertThat(entry.suborderLabel()).isEqualTo("ABC/01 - Wartung und Betrieb");
      assertThat(entry.groupName()).isEqualTo("Wartung");
      assertThat(entry.duration()).hasMinutes(90);
    }

    @Test
    void the_favourite_of_somebody_else_is_not_handed_out() {
      when(favoriteRepository.findById(8L)).thenReturn(Optional.of(favoriteOn(FOREIGN_ORDER)));

      assertThatThrownBy(() -> favoriteService.getOwnFavorite(8L)).isInstanceOf(AuthorizationException.class);
    }
  }

  @Nested
  class Using {

    @Test
    void applying_an_own_favourite_notes_the_time() {
      var favorite = favoriteOn(MY_ORDER);
      when(favoriteRepository.findById(5L)).thenReturn(Optional.of(favorite));

      favoriteService.markUsed(5L);

      assertThat(favorite.getLastUsed()).isNotNull();
    }

    @Test
    void the_favourite_of_somebody_else_is_not_touched() {
      var favorite = favoriteOn(FOREIGN_ORDER);
      when(favoriteRepository.findById(6L)).thenReturn(Optional.of(favorite));

      assertThatThrownBy(() -> favoriteService.markUsed(6L)).isInstanceOf(AuthorizationException.class);
      assertThat(favorite.getLastUsed()).isNull();
    }

    @Test
    void only_an_own_favourite_is_deleted() {
      when(favoriteRepository.findById(5L)).thenReturn(Optional.of(favoriteOn(MY_ORDER)));
      when(favoriteRepository.findById(6L)).thenReturn(Optional.of(favoriteOn(FOREIGN_ORDER)));

      favoriteService.deleteFavorite(5L);
      assertThatThrownBy(() -> favoriteService.deleteFavorite(6L)).isInstanceOf(AuthorizationException.class);

      verify(favoriteRepository).deleteById(5L);
      verify(favoriteRepository, never()).deleteById(6L);
    }
  }

  @Nested
  class Groups {

    @Test
    void a_new_group_belongs_to_the_login_and_comes_last() {
      givenGroups(group(5L, ME, "Wartung", 0), group(6L, ME, "Projekt", 3));

      var id = favoriteService.createGroup("  Kunde A  ");

      var saved = ArgumentCaptor.forClass(FavoriteGroup.class);
      verify(favoriteGroupRepository).saveAndFlush(saved.capture());
      assertThat(id).isEqualTo(77L);
      assertThat(saved.getValue().getEmployeeId()).isEqualTo(ME);
      assertThat(saved.getValue().getName()).isEqualTo("Kunde A");
      assertThat(saved.getValue().getPosition()).isEqualTo(4);
    }

    @Test
    void a_name_is_required_and_short() {
      assertThatThrownBy(() -> favoriteService.createGroup("  ")).isInstanceOf(InvalidDataException.class);
      assertThatThrownBy(() -> favoriteService.createGroup("x".repeat(65))).isInstanceOf(InvalidDataException.class);
    }

    /** The database compares names case-insensitively; the check says so before it does. */
    @Test
    void a_name_is_taken_once_per_person_regardless_of_case() {
      givenGroups(group(5L, ME, "Wartung", 0));

      assertThatThrownBy(() -> favoriteService.createGroup("wartung")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void a_group_keeps_its_name_when_renamed_to_itself() {
      var maintenance = group(5L, ME, "Wartung", 0);
      givenGroups(maintenance);

      favoriteService.renameGroup(5L, "WARTUNG");

      assertThat(maintenance.getName()).isEqualTo("WARTUNG");
    }

    @Test
    void the_group_of_somebody_else_is_neither_renamed_nor_deleted() {
      var foreign = group(6L, SOMEBODY_ELSE, "Fremd", 0);
      when(favoriteGroupRepository.findById(6L)).thenReturn(Optional.of(foreign));

      assertThatThrownBy(() -> favoriteService.renameGroup(6L, "Meins")).isInstanceOf(AuthorizationException.class);
      assertThatThrownBy(() -> favoriteService.deleteGroup(6L)).isInstanceOf(AuthorizationException.class);

      assertThat(foreign.getName()).isEqualTo("Fremd");
      verify(favoriteGroupRepository, never()).delete(any());
    }

    /** Its favourites stay, behind those already placed without a group, in the order they had. */
    @Test
    void deleting_a_group_keeps_its_favourites_without_a_group() {
      var maintenance = group(5L, ME, "Wartung", 0);
      givenGroups(maintenance);
      var second = favorite(1L, maintenance, 1, MONDAY);
      var first = favorite(2L, maintenance, 0, MONDAY);
      when(favoriteRepository.findAllByGroupId(5L)).thenReturn(List.of(second, first));
      when(favoriteRepository.findMaxPositionWithoutGroup(ME)).thenReturn(3);

      favoriteService.deleteGroup(5L);

      assertThat(first.getGroup()).isNull();
      assertThat(second.getGroup()).isNull();
      assertThat(first.getPosition()).isEqualTo(4);
      assertThat(second.getPosition()).isEqualTo(5);
      verify(favoriteGroupRepository).delete(maintenance);
    }
  }

  @Nested
  class Arranging {

    private FavoriteGroup maintenance;
    private FavoriteGroup project;
    private Favorite a;
    private Favorite b;
    private Favorite c;

    @BeforeEach
    void aGroupedList() {
      maintenance = group(5L, ME, "Wartung", 0);
      project = group(6L, ME, "Projekt", 1);
      givenGroups(maintenance, project);
      a = favorite(1L, null, 0, MONDAY);
      b = favorite(2L, maintenance, 0, MONDAY);
      c = favorite(3L, maintenance, 1, MONDAY);
      givenFavorites(a, b, c);
    }

    @Test
    void in_the_own_order_every_favourite_takes_its_section_and_place_and_the_groups_their_order() {
      preferences.put(FavoritePreferences.MODULE_KEY, Map.of("sortOrder", "custom"));

      favoriteService.arrange(layout("group:", "favorite:3", "group:6", "favorite:1", "group:5", "favorite:2"));

      assertThat(project.getPosition()).isZero();
      assertThat(maintenance.getPosition()).isEqualTo(1);
      assertThat(c.getGroup()).isNull();
      assertThat(c.getPosition()).isZero();
      assertThat(a.getGroup()).isSameAs(project);
      assertThat(a.getPosition()).isZero();
      assertThat(b.getGroup()).isSameAs(maintenance);
      assertThat(b.getPosition()).isZero();
    }

    /** Ordered by use the places within a group are not what the person saw, so they stay. */
    @Test
    void ordered_by_use_only_a_change_of_group_counts_and_lands_on_top() {
      favoriteService.arrange(layout("group:", "favorite:1", "group:5", "favorite:3", "group:6", "favorite:2"));

      assertThat(c.getGroup()).isSameAs(maintenance);
      assertThat(c.getPosition()).isEqualTo(1);
      assertThat(b.getGroup()).isSameAs(project);
      assertThat(b.getPosition()).isZero();
      assertThat(a.getPosition()).isZero();
    }

    @Test
    void a_favourite_of_somebody_else_is_refused() {
      when(favoriteRepository.existsById(9L)).thenReturn(true);

      assertThatThrownBy(() -> favoriteService.arrange(layout("group:", "favorite:9", "favorite:1")))
          .isInstanceOf(AuthorizationException.class);
    }

    @Test
    void a_group_of_somebody_else_is_refused() {
      when(favoriteGroupRepository.existsById(8L)).thenReturn(true);

      assertThatThrownBy(() -> favoriteService.arrange(layout("group:", "group:8", "favorite:1")))
          .isInstanceOf(AuthorizationException.class);
      assertThat(a.getGroup()).isNull();
    }

    /** Deleted in another window meanwhile: nothing to arrange, nothing to refuse. */
    @Test
    void what_no_longer_exists_is_skipped() {
      preferences.put(FavoritePreferences.MODULE_KEY, Map.of("sortOrder", "custom"));

      favoriteService.arrange(layout("group:", "favorite:404", "favorite:2", "group:404", "favorite:1"));

      assertThat(b.getGroup()).isNull();
      assertThat(b.getPosition()).isZero();
      assertThat(a.getGroup()).isNull();
    }

    @Test
    void a_favourite_named_twice_is_refused() {
      assertThatThrownBy(() -> favoriteService.arrange(layout("group:", "favorite:1", "group:5", "favorite:1")))
          .isInstanceOf(InvalidDataException.class);
    }
  }

  @Nested
  class Preferences {

    @Test
    void the_sort_order_is_a_preference_of_the_login_and_the_default_leaves_nothing_behind() {
      assertThat(favoriteService.getSortOrder()).isEqualTo(RECENT);

      favoriteService.setSortOrder(CUSTOM);
      preferences.put(FavoritePreferences.MODULE_KEY, Map.of("sortOrder", "custom"));
      favoriteService.setSortOrder(RECENT);

      verify(userPreferenceService).saveModuleSettings(FavoritePreferences.MODULE_KEY, Map.of("sortOrder", "custom"));
      verify(userPreferenceService).saveModuleSettings(eq(FavoritePreferences.MODULE_KEY), eq(Map.of()));
    }

    /** Ten until the person chooses (#1414). */
    @Test
    void the_short_list_shows_ten_until_the_person_chooses() {
      assertThat(favoriteService.getListSize()).isEqualTo(10);
    }

    @Test
    void the_size_of_the_short_list_is_kept_next_to_the_sort_order() {
      preferences.put(FavoritePreferences.MODULE_KEY, Map.of("sortOrder", "custom"));

      favoriteService.setListSize(25);

      verify(userPreferenceService).saveModuleSettings(FavoritePreferences.MODULE_KEY,
          Map.of("sortOrder", "custom", "listSize", "25"));
    }

    @Test
    void a_size_out_of_range_is_refused_and_nothing_is_stored() {
      assertThatThrownBy(() -> favoriteService.setListSize(0)).isInstanceOf(InvalidDataException.class);
      assertThatThrownBy(() -> favoriteService.setListSize(51)).isInstanceOf(InvalidDataException.class);

      verify(userPreferenceService, never()).saveModuleSettings(anyString(), any());
    }
  }

  @Nested
  class ShortList {

    /** The ones used last, no matter their group or the own order; the count names all of them. */
    @Test
    void it_holds_the_favourites_used_last_up_to_the_chosen_number() {
      preferences.put(FavoritePreferences.MODULE_KEY, Map.of("sortOrder", "custom", "listSize", "2"));
      var maintenance = group(5L, ME, "Wartung", 0);
      givenGroups(maintenance);
      givenFavorites(favorite(1L, null, 0, MONDAY), favorite(2L, maintenance, 0, MONDAY.plusDays(2)),
          favorite(3L, null, 1, null), favorite(4L, null, 2, MONDAY.plusDays(1)));

      var recent = favoriteService.getRecentFavorites();

      assertThat(recent.favorites()).extracting(FavoriteEntry::id).containsExactly(2L, 4L);
      assertThat(recent.total()).isEqualTo(4);
    }

    @Test
    void with_fewer_favourites_than_the_number_it_holds_them_all() {
      givenFavorites(favorite(1L, null, null, MONDAY), favorite(2L, null, null, null));

      var recent = favoriteService.getRecentFavorites();

      assertThat(recent.favorites()).extracting(FavoriteEntry::id).containsExactly(1L, 2L);
      assertThat(recent.total()).isEqualTo(2);
    }
  }

  private void givenGroups(FavoriteGroup... groups) {
    var own = Arrays.stream(groups).filter(group -> group.getEmployeeId() == ME).toList();
    when(favoriteGroupRepository.findAllByEmployeeId(ME)).thenReturn(own);
    for (var group : groups) {
      when(favoriteGroupRepository.findById(group.getId())).thenReturn(Optional.of(group));
    }
  }

  private void givenFavorites(Favorite... favorites) {
    when(favoriteRepository.findAllByEmployeeId(ME)).thenReturn(new ArrayList<>(List.of(favorites)));
  }

  private static FavoriteLayout layout(String... tokens) {
    return FavoriteLayout.parse(List.of(tokens));
  }

  private static List<Long> ids(FavoriteSection section) {
    return SectionIds.of(section);
  }

  private static FavoriteGroup group(long id, long employeeId, String name, int position) {
    var group = new FavoriteGroup();
    setField(group, "id", id);
    group.setEmployee(employeeWithId(employeeId));
    group.setName(name);
    group.setPosition(position);
    return group;
  }

  private static Favorite favorite(long id, FavoriteGroup group, Integer position, LocalDateTime lastUsed) {
    var favorite = Favorite.builder().employeeorder(employeeorderOnSuborder()).hours(1).minutes(30)
        .group(group).position(position).lastUsed(lastUsed).build();
    setField(favorite, "id", id);
    return favorite;
  }

  private static Employeeorder employeeorderOnSuborder() {
    var suborder = suborderWithId(3L);
    setField(suborder, "completeOrderSign", "ABC/01");
    suborder.setShortdescription("Wartung und Betrieb");
    var employeeorder = employeeorderWithId(MY_ORDER);
    employeeorder.setSuborder(suborder);
    return employeeorder;
  }

  private static Favorite favoriteOn(long employeeorderId) {
    return Favorite.builder().employeeorder(employeeorderWithId(employeeorderId)).hours(1).minutes(0).build();
  }

  /** The ids of a section in display order. */
  private static final class SectionIds {

    static List<Long> of(FavoriteSection section) {
      return section.favorites().stream().map(FavoriteEntry::id).toList();
    }
  }
}
