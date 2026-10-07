package de.hbt.salat.favorites.service;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;
import static java.util.Comparator.reverseOrder;
import static de.hbt.salat.common.exception.ErrorCode.AA_REQUIRED;
import static de.hbt.salat.common.exception.ErrorCode.FA_EMPLOYEE_ORDER_NOT_OWN;
import static de.hbt.salat.common.exception.ErrorCode.FA_FAVORITE_NOT_OWN;
import static de.hbt.salat.common.exception.ErrorCode.FA_FAVORITE_NOT_OWN_USE;
import static de.hbt.salat.common.exception.ErrorCode.FA_GROUP_NAME_INVALID;
import static de.hbt.salat.common.exception.ErrorCode.FA_GROUP_NAME_TAKEN;
import static de.hbt.salat.common.exception.ErrorCode.FA_GROUP_NOT_OWN;
import static de.hbt.salat.common.exception.ErrorCode.FA_LAYOUT_INVALID;
import static de.hbt.salat.common.exception.ErrorCode.FA_LIST_SIZE_INVALID;
import static de.hbt.salat.favorites.domain.FavoriteSortOrder.CUSTOM;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.util.ClockProvider;
import de.hbt.salat.common.util.TicketReferences;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.favorites.domain.Favorite;
import de.hbt.salat.favorites.domain.FavoriteEntry;
import de.hbt.salat.favorites.domain.FavoriteGroup;
import de.hbt.salat.favorites.domain.FavoriteGroupOption;
import de.hbt.salat.favorites.domain.FavoriteLayout;
import de.hbt.salat.favorites.domain.FavoriteList;
import de.hbt.salat.favorites.domain.FavoritePreferences;
import de.hbt.salat.favorites.domain.FavoriteSection;
import de.hbt.salat.favorites.domain.FavoriteSortOrder;
import de.hbt.salat.favorites.domain.NewFavorite;
import de.hbt.salat.favorites.domain.RecentFavorites;
import de.hbt.salat.favorites.persistence.EmployeeReferences;
import de.hbt.salat.favorites.persistence.EmployeeorderReferences;
import de.hbt.salat.favorites.persistence.FavoriteGroupRepository;
import de.hbt.salat.favorites.persistence.FavoriteRepository;
import de.hbt.salat.order.service.EmployeeorderService;
import de.hbt.salat.settings.service.UserPreferenceService;

/**
 * The favourites of the logged-in person, their groups and their order (#1414).
 *
 * <p>Everything here is the person's own: a favourite belongs to the person of its employee order
 * (#1369), a group to the person it was created for. Nobody sees or changes the favourites or groups
 * of somebody else — every method that takes an id checks it, because the ids arrive from the
 * browser. The sort order is a preference of the login, like the other preferences of the booking
 * screens.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
@Authorized
public class FavoriteService {

  /** The favourite applied last first; never applied ones behind, the one created last first. */
  private static final Comparator<Favorite> RECENTLY_USED_FIRST =
      comparing(Favorite::getLastUsed, nullsLast(reverseOrder()))
          .thenComparing(Favorite::getId, nullsLast(reverseOrder()));

  /** The person's own order; favourites never placed by hand behind the placed ones. */
  private static final Comparator<Favorite> OWN_ORDER =
      comparing(Favorite::getPosition, nullsLast(naturalOrder())).thenComparing(RECENTLY_USED_FIRST);

  private final FavoriteRepository favoriteRepository;
  private final FavoriteGroupRepository favoriteGroupRepository;
  private final AuthorizedEmployee authorizedEmployee;
  private final EmployeeorderReferences employeeorderReferences;
  private final EmployeeReferences employeeReferences;
  private final EmployeeorderService employeeorderService;
  private final UserPreferenceService userPreferenceService;

  /** The favourites of the logged-in person, grouped and ordered as the lists show them. */
  @Transactional(readOnly = true)
  public FavoriteList getOwnFavoriteList() {
    long me = ownEmployeeId();
    return arrange(preferences().sortOrder(), favoriteGroupRepository.findAllByEmployeeId(me),
        favoriteRepository.findAllByEmployeeId(me));
  }

  /**
   * The short list of the booking pages (#1414): the favourites of the logged-in person used last,
   * at most as many as they chose, without regard to groups — and how many there are in all.
   */
  @Transactional(readOnly = true)
  public RecentFavorites getRecentFavorites() {
    var favorites = favoriteRepository.findAllByEmployeeId(ownEmployeeId());
    return new RecentFavorites(favorites.stream()
        .sorted(RECENTLY_USED_FIRST)
        .limit(preferences().listSize())
        .map(FavoriteService::entryOf)
        .toList(), favorites.size());
  }

  /** The groups of the logged-in person in their order, to choose one when saving a favourite. */
  @Transactional(readOnly = true)
  public List<FavoriteGroupOption> getOwnGroups() {
    return favoriteGroupRepository.findAllByEmployeeId(ownEmployeeId()).stream()
        .map(group -> new FavoriteGroupOption(group.getId(), group.getName()))
        .toList();
  }

  /**
   * A favourite of the logged-in person; empty when there is none with this id.
   *
   * @throws AuthorizationException when it belongs to somebody else
   */
  @Transactional(readOnly = true)
  public Optional<FavoriteEntry> getOwnFavorite(long favoriteId) {
    return favoriteRepository.findById(favoriteId)
        .map(favorite -> {
          requireOwn(favorite, FA_FAVORITE_NOT_OWN_USE);
          return entryOf(favorite);
        });
  }

  /**
   * Adds a favourite and stores its ticket references under the rule a booking stores them under
   * (#1326).
   *
   * <p>The person of a favourite is the person of its employee order (#1369). An employee order of
   * somebody else would put the favourite into that person's list, so it is refused. This is the
   * one place that refuses it: the booking form does not offer the favourite for somebody else, but
   * relies on this check, the REST client has nothing else.
   *
   * <p>A new favourite counts as just used, and it stands at the top of its group, or of the
   * favourites without a group (#1414) — in both orders it is the first one the person sees.
   *
   * @return the id of the new favourite
   */
  public long addFavorite(NewFavorite newFavorite) {
    if (!ownsEmployeeorder(newFavorite.employeeorderId())) {
      throw new AuthorizationException(FA_EMPLOYEE_ORDER_NOT_OWN);
    }
    var group = newFavorite.groupId() == null ? null : ownGroup(newFavorite.groupId());
    var top = group == null
        ? favoriteRepository.findMinPositionWithoutGroup(ownEmployeeId())
        : favoriteRepository.findMinPositionInGroup(group.getId());
    var favorite = Favorite.builder()
        .employeeorder(employeeorderReferences.employeeorder(newFavorite.employeeorderId()))
        .hours(newFavorite.hours())
        .minutes(newFavorite.minutes())
        .comment(newFavorite.comment())
        .ticketReferences(new ArrayList<>(TicketReferences.normalize(newFavorite.ticketReferences())))
        .group(group)
        .position(top == null ? 0 : top - 1)
        .lastUsed(ClockProvider.now())
        .build();
    try {
      // flushed here, inside the try: the ticket references would otherwise only be written at commit,
      // and a violation there would pass this catch
      return favoriteRepository.saveAndFlush(favorite).getId();
    } catch (DataIntegrityViolationException e) {
      log.error("Could not save {}.", favorite, e);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  /**
   * Whether a favourite on this employee order is the login's own (#1369). The favourite has no
   * person of its own; whose the employee order is, the order module answers.
   */
  public boolean isOwnEmployeeorder(long employeeorderId) {
    return ownsEmployeeorder(employeeorderId);
  }

  /**
   * Notes that the favourite was just applied (#1414): the list ordered by use shows it first.
   *
   * @throws AuthorizationException when it belongs to somebody else
   */
  public void markUsed(long favoriteId) {
    favoriteRepository.findById(favoriteId).ifPresent(favorite -> {
      requireOwn(favorite, FA_FAVORITE_NOT_OWN_USE);
      favorite.setLastUsed(ClockProvider.now());
    });
  }

  public void deleteFavorite(long id) {
    var favorite = favoriteRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Favorite not found for id " + id));
    if (ownsEmployeeorder(favorite.getEmployeeorderId())) {
      favoriteRepository.deleteById(id);
    } else {
      throw new AuthorizationException(FA_FAVORITE_NOT_OWN);
    }
  }

  /** The sort order the logged-in person chose; {@link FavoriteSortOrder#RECENT} until they choose. */
  @Transactional(readOnly = true)
  public FavoriteSortOrder getSortOrder() {
    return preferences().sortOrder();
  }

  public void setSortOrder(FavoriteSortOrder sortOrder) {
    savePreferences(preferences().withSortOrder(sortOrder));
  }

  /** How many favourites the short lists show (#1414); {@link FavoritePreferences#DEFAULT_LIST_SIZE} until chosen. */
  @Transactional(readOnly = true)
  public int getListSize() {
    return preferences().listSize();
  }

  /**
   * Sets how many favourites the short lists show (#1414).
   *
   * @throws InvalidDataException outside {@link FavoritePreferences#MIN_LIST_SIZE} to
   *                              {@link FavoritePreferences#MAX_LIST_SIZE}
   */
  public void setListSize(int listSize) {
    if (!FavoritePreferences.isValidListSize(listSize)) {
      throw new InvalidDataException(FA_LIST_SIZE_INVALID,
          FavoritePreferences.MIN_LIST_SIZE, FavoritePreferences.MAX_LIST_SIZE);
    }
    savePreferences(preferences().withListSize(listSize));
  }

  /**
   * Creates a group of the logged-in person below their other groups.
   *
   * @return the id of the new group
   */
  public long createGroup(String name) {
    long me = ownEmployeeId();
    var groups = favoriteGroupRepository.findAllByEmployeeId(me);
    var validName = validGroupName(name, groups, null);
    var group = new FavoriteGroup();
    group.setEmployee(employeeReferences.employee(me));
    group.setName(validName);
    group.setPosition(groups.stream().mapToInt(FavoriteGroup::getPosition).max().orElse(-1) + 1);
    return favoriteGroupRepository.saveAndFlush(group).getId();
  }

  public void renameGroup(long groupId, String name) {
    var group = ownGroup(groupId);
    group.setName(validGroupName(name, favoriteGroupRepository.findAllByEmployeeId(ownEmployeeId()), groupId));
  }

  /**
   * Deletes a group of the logged-in person. Its favourites stay; they stand without a group
   * afterwards, behind the ones that were placed there already, in the order they had in the group.
   */
  public void deleteGroup(long groupId) {
    var group = ownGroup(groupId);
    var next = Optional.ofNullable(favoriteRepository.findMaxPositionWithoutGroup(ownEmployeeId()))
        .map(max -> max + 1)
        .orElse(0);
    for (var favorite : favoriteRepository.findAllByGroupId(groupId).stream().sorted(OWN_ORDER).toList()) {
      favorite.setGroup(null);
      favorite.setPosition(next++);
    }
    favoriteRepository.flush();
    favoriteGroupRepository.delete(group);
  }

  /**
   * Takes over the arrangement of the dialog (#1414). The groups take the order of the layout. In
   * the own order every favourite takes its section and its place in it. Ordered by use, the places
   * within a group are not what the person sees, so only a change of group counts: the favourite
   * moves to the top of its new group, and the own order stays as it was.
   *
   * <p>A favourite or group that no longer exists — deleted in another window — is skipped; one of
   * somebody else is refused.
   */
  public void arrange(FavoriteLayout layout) {
    long me = ownEmployeeId();
    var groups = byId(favoriteGroupRepository.findAllByEmployeeId(me), FavoriteGroup::getId);
    var favorites = byId(favoriteRepository.findAllByEmployeeId(me), Favorite::getId);
    var sections = knownSections(layout, groups, favorites);

    int groupPosition = 0;
    for (var section : sections) {
      if (section.groupId() != null) {
        groups.get(section.groupId()).setPosition(groupPosition++);
      }
    }
    boolean ownOrder = preferences().sortOrder() == CUSTOM;
    for (var section : sections) {
      var group = section.groupId() == null ? null : groups.get(section.groupId());
      for (int index = 0; index < section.favoriteIds().size(); index++) {
        var favorite = favorites.get(section.favoriteIds().get(index));
        if (ownOrder) {
          favorite.setGroup(group);
          favorite.setPosition(index);
        } else if (!Objects.equals(favorite.getGroupId(), section.groupId())) {
          favorite.setPosition(topPositionAmong(favorites.values(), section.groupId()));
          favorite.setGroup(group);
        }
      }
    }
  }

  /**
   * Orders the favourites of a person for display: the ones without a group first, then the groups
   * in their order, each with its favourites in the chosen order.
   */
  static FavoriteList arrange(FavoriteSortOrder sortOrder, List<FavoriteGroup> groups, List<Favorite> favorites) {
    var order = sortOrder == CUSTOM ? OWN_ORDER : RECENTLY_USED_FIRST;
    var groupIds = groups.stream().map(FavoriteGroup::getId).collect(Collectors.toSet());
    var sections = new ArrayList<FavoriteSection>();
    sections.add(new FavoriteSection(null, null, favorites.stream()
        .filter(favorite -> favorite.getGroupId() == null || !groupIds.contains(favorite.getGroupId()))
        .sorted(order)
        .map(FavoriteService::entryOf)
        .toList()));
    for (var group : groups) {
      sections.add(new FavoriteSection(group.getId(), group.getName(), favorites.stream()
          .filter(favorite -> group.getId().equals(favorite.getGroupId()))
          .sorted(order)
          .map(FavoriteService::entryOf)
          .toList()));
    }
    return new FavoriteList(sortOrder, sections);
  }

  private static FavoriteEntry entryOf(Favorite favorite) {
    var group = favorite.getGroup();
    return new FavoriteEntry(favorite.getId(), favorite.getEmployeeorderId(),
        favorite.getEmployeeorder().getSuborder().getCompleteOrderSignAndDescription(),
        favorite.getHours() == null ? 0 : favorite.getHours(),
        favorite.getMinutes() == null ? 0 : favorite.getMinutes(),
        favorite.getComment(), favorite.getTicketReferences(),
        group == null ? null : group.getId(), group == null ? null : group.getName(),
        favorite.getLastUsed());
  }

  /**
   * The sections of the layout with what still exists. Refuses ids of somebody else and an id named
   * twice — the latter would leave it to the order of the loop where the favourite ends up.
   */
  private List<FavoriteLayout.Section> knownSections(FavoriteLayout layout, Map<Long, FavoriteGroup> groups,
                                                     Map<Long, Favorite> favorites) {
    var seenGroups = new HashSet<Long>();
    var seenFavorites = new HashSet<Long>();
    boolean seenUngrouped = false;
    var sections = new ArrayList<FavoriteLayout.Section>();
    for (var section : layout.sections()) {
      if (section.groupId() == null) {
        if (seenUngrouped) {
          throw new InvalidDataException(FA_LAYOUT_INVALID);
        }
        seenUngrouped = true;
      } else {
        if (!seenGroups.add(section.groupId())) {
          throw new InvalidDataException(FA_LAYOUT_INVALID);
        }
        if (!groups.containsKey(section.groupId())) {
          if (favoriteGroupRepository.existsById(section.groupId())) {
            throw new AuthorizationException(FA_GROUP_NOT_OWN);
          }
          continue;
        }
      }
      var favoriteIds = new ArrayList<Long>();
      for (var favoriteId : section.favoriteIds()) {
        if (!seenFavorites.add(favoriteId)) {
          throw new InvalidDataException(FA_LAYOUT_INVALID);
        }
        if (favorites.containsKey(favoriteId)) {
          favoriteIds.add(favoriteId);
        } else if (favoriteRepository.existsById(favoriteId)) {
          throw new AuthorizationException(FA_FAVORITE_NOT_OWN_USE);
        }
      }
      sections.add(new FavoriteLayout.Section(section.groupId(), favoriteIds));
    }
    return sections;
  }

  private static int topPositionAmong(Iterable<Favorite> favorites, Long groupId) {
    int top = 0;
    for (var favorite : favorites) {
      if (Objects.equals(favorite.getGroupId(), groupId) && favorite.getPosition() != null) {
        top = Math.min(top, favorite.getPosition() - 1);
      }
    }
    return top;
  }

  private String validGroupName(String name, List<FavoriteGroup> groups, Long renamedGroupId) {
    var trimmed = name == null ? "" : name.trim();
    if (trimmed.isEmpty() || trimmed.length() > FavoriteGroup.NAME_MAX_LENGTH) {
      throw new InvalidDataException(FA_GROUP_NAME_INVALID);
    }
    // the database compares case-insensitively, the check does the same
    boolean taken = groups.stream()
        .filter(group -> !group.getId().equals(renamedGroupId))
        .anyMatch(group -> group.getName().equalsIgnoreCase(trimmed));
    if (taken) {
      throw new BusinessRuleException(FA_GROUP_NAME_TAKEN, trimmed);
    }
    return trimmed;
  }

  private FavoriteGroup ownGroup(long groupId) {
    var group = favoriteGroupRepository.findById(groupId)
        .orElseThrow(() -> new InvalidDataException(FA_GROUP_NOT_OWN));
    if (!Objects.equals(group.getEmployeeId(), authorizedEmployee.getEmployeeId())) {
      throw new AuthorizationException(FA_GROUP_NOT_OWN);
    }
    return group;
  }

  private void requireOwn(Favorite favorite, ErrorCode refusal) {
    if (!ownsEmployeeorder(favorite.getEmployeeorderId())) {
      throw new AuthorizationException(refusal);
    }
  }

  private boolean ownsEmployeeorder(long employeeorderId) {
    return employeeorderService.getEmployeeIdOfEmployeeorder(employeeorderId)
        .filter(authorizedEmployee.getEmployeeId()::equals)
        .isPresent();
  }

  private long ownEmployeeId() {
    var employeeId = authorizedEmployee.getEmployeeId();
    if (employeeId == null) {
      // a login without a person has no favourites to read or arrange
      throw new AuthorizationException(AA_REQUIRED);
    }
    return employeeId;
  }

  private FavoritePreferences preferences() {
    return FavoritePreferences.from(userPreferenceService.getModuleSettings(FavoritePreferences.MODULE_KEY));
  }

  private void savePreferences(FavoritePreferences preferences) {
    userPreferenceService.saveModuleSettings(FavoritePreferences.MODULE_KEY, preferences.toMap());
  }

  private static <T> Map<Long, T> byId(List<T> entities, Function<T, Long> id) {
    var map = new LinkedHashMap<Long, T>();
    entities.forEach(entity -> map.put(id.apply(entity), entity));
    return map;
  }
}
