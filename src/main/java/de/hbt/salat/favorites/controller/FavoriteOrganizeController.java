package de.hbt.salat.favorites.controller;

import static de.hbt.salat.common.exception.ErrorCode.FA_LAYOUT_INVALID;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.favorites.domain.FavoriteLayout;
import de.hbt.salat.favorites.domain.FavoriteSortOrder;
import de.hbt.salat.favorites.service.FavoriteService;

/**
 * The dialog "Favoriten ordnen" (#1414): groups, their order, the order within them and the sort
 * order. Every page that shows a favourites list opens it; each action answers with the dialog's
 * body again, so the browser always shows what the server stored.
 *
 * <p>Open to every login, restricted ones included, like the booking screens that offer the
 * favourites: everything here is the person's own, and {@link FavoriteService} checks every id.
 */
@Controller
@RequestMapping("/favorites/organize")
@RequiredArgsConstructor
@Authorized
public class FavoriteOrganizeController {

  static final String BODY = "favorites/organize :: organizeBody";

  private final FavoriteService favoriteService;
  private final ErrorCodeViewHelper errorCodeViewHelper;

  @GetMapping
  public String show(Model model) {
    return body(model, false);
  }

  @PostMapping("/sort-order")
  public String setSortOrder(@RequestParam String sortOrder, Model model) {
    return perform(model, () -> favoriteService.setSortOrder(
        FavoriteSortOrder.ofKey(sortOrder).orElseThrow(() -> new InvalidDataException(FA_LAYOUT_INVALID))));
  }

  @PostMapping("/groups")
  public String createGroup(@RequestParam(required = false) String name, Model model) {
    return perform(model, () -> favoriteService.createGroup(name));
  }

  @PostMapping("/groups/{groupId}/rename")
  public String renameGroup(@PathVariable long groupId, @RequestParam(required = false) String name, Model model) {
    return perform(model, () -> favoriteService.renameGroup(groupId, name));
  }

  @PostMapping("/groups/{groupId}/delete")
  public String deleteGroup(@PathVariable long groupId, Model model) {
    return perform(model, () -> favoriteService.deleteGroup(groupId));
  }

  /** The arrangement after a drag and drop or an arrow button, as tokens in the order of the page. */
  @PostMapping("/arrange")
  public String arrange(@RequestParam(required = false) List<String> layout, Model model) {
    return perform(model, () -> favoriteService.arrange(parse(layout)));
  }

  private static FavoriteLayout parse(List<String> layout) {
    try {
      return FavoriteLayout.parse(layout);
    } catch (IllegalArgumentException e) {
      throw new InvalidDataException(FA_LAYOUT_INVALID);
    }
  }

  private String perform(Model model, Runnable action) {
    try {
      action.run();
    } catch (ErrorCodeException e) {
      model.addAttribute("errors", errorCodeViewHelper.toViewMessages(e));
    }
    return body(model, true);
  }

  /**
   * @param changed whether an action ran: the page behind the dialog shows the favourites as well and
   *                is reloaded when the dialog closes after a change
   */
  private String body(Model model, boolean changed) {
    var favorites = favoriteService.getOwnFavoriteList();
    model.addAttribute("favoriteList", favorites);
    model.addAttribute("ungrouped", favorites.sections().getFirst());
    model.addAttribute("groups", favorites.sections().subList(1, favorites.sections().size()));
    model.addAttribute("customOrder", favorites.sortOrder() == FavoriteSortOrder.CUSTOM);
    model.addAttribute("changed", changed);
    return BODY;
  }
}
