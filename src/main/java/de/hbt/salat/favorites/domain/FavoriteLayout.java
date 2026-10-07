package de.hbt.salat.favorites.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * The arrangement the dialog sends after a drag and drop or an arrow button (#1414): the sections
 * from the top, each with its favourites from the top. The section without a group comes first;
 * the others name their group, and their order is the order of the groups.
 *
 * <p>The dialog writes it as a sequence of tokens in the order of the page — {@code group:} for the
 * section without a group, {@code group:<id>} for a group, {@code favorite:<id>} for a favourite of
 * the section before it. Reading the order off the page means the browser keeps no second model of
 * what it shows.
 */
public record FavoriteLayout(List<Section> sections) {

  public static final String GROUP_PREFIX = "group:";
  public static final String FAVORITE_PREFIX = "favorite:";

  public FavoriteLayout {
    sections = List.copyOf(sections);
  }

  /** A section; {@code groupId} is {@code null} for the favourites without a group. */
  public record Section(Long groupId, List<Long> favoriteIds) {

    public Section {
      favoriteIds = List.copyOf(favoriteIds);
    }
  }

  /**
   * Reads the tokens of the dialog.
   *
   * @throws IllegalArgumentException for a token it does not know, or a favourite before the first section
   */
  public static FavoriteLayout parse(List<String> tokens) {
    var sections = new ArrayList<Section>();
    Long currentGroup = null;
    List<Long> currentFavorites = null;
    for (var token : tokens == null ? List.<String>of() : tokens) {
      if (token.startsWith(GROUP_PREFIX)) {
        if (currentFavorites != null) {
          sections.add(new Section(currentGroup, currentFavorites));
        }
        var id = token.substring(GROUP_PREFIX.length());
        currentGroup = id.isEmpty() ? null : Long.valueOf(id);
        currentFavorites = new ArrayList<>();
      } else if (token.startsWith(FAVORITE_PREFIX) && currentFavorites != null) {
        currentFavorites.add(Long.valueOf(token.substring(FAVORITE_PREFIX.length())));
      } else {
        throw new IllegalArgumentException("unexpected layout token " + token);
      }
    }
    if (currentFavorites != null) {
      sections.add(new Section(currentGroup, currentFavorites));
    }
    return new FavoriteLayout(sections);
  }
}
