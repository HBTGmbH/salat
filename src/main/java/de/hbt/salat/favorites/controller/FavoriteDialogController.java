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
 * The dialog "Favoriten" (#1414): every favourite of the person, by group, to search and pick from;
 * behind the switch "Ordnen" the groups, their order, the order within them and the sort order.
 * The booking pages open it from their short lists. Each action answers with the dialog's body again,
 * so the browser always shows what the server stored.
 *
 * <p>Picking a favourite is not handled here: the page that opens the dialog provides the form it is
 * applied with (the booking day knows the day and the area to refresh), so applying from the dialog and
 * from the short list are one and the same request.
 *
 * <p>Open to every login, restricted ones included, like the booking screens that offer the
 * favourites: everything here is the person's own, and {@link FavoriteService} checks every id.
 */
@Controller
@RequestMapping("/favorites")
@RequiredArgsConstructor
@Authorized
public class FavoriteDialogController {

  static final String BODY = "favorites/dialog :: dialogBody";

  private final FavoriteService favoriteService;
  private final ErrorCodeViewHelper errorCodeViewHelper;

  /** @param organize whether to show the dialog in the mode that arranges, instead of the one that picks */
  @GetMapping("/dialog")
  public String show(@RequestParam(defaultValue = "false") boolean organize, Model model) {
    return body(model, organize, false);
  }

  @PostMapping("/organize/sort-order")
  public String setSortOrder(@RequestParam String sortOrder, Model model) {
    return organize(model, () -> favoriteService.setSortOrder(
        FavoriteSortOrder.ofKey(sortOrder).orElseThrow(() -> new InvalidDataException(FA_LAYOUT_INVALID))));
  }

  @PostMapping("/organize/groups")
  public String createGroup(@RequestParam(required = false) String name, Model model) {
    return organize(model, () -> favoriteService.createGroup(name));
  }

  @PostMapping("/organize/groups/{groupId}/rename")
  public String renameGroup(@PathVariable long groupId, @RequestParam(required = false) String name, Model model) {
    return organize(model, () -> favoriteService.renameGroup(groupId, name));
  }

  @PostMapping("/organize/groups/{groupId}/delete")
  public String deleteGroup(@PathVariable long groupId, Model model) {
    return organize(model, () -> favoriteService.deleteGroup(groupId));
  }

  /** The arrangement after a drag and drop or an arrow button, as tokens in the order of the page. */
  @PostMapping("/organize/arrange")
  public String arrange(@RequestParam(required = false) List<String> layout, Model model) {
    return organize(model, () -> favoriteService.arrange(parse(layout)));
  }

  private static FavoriteLayout parse(List<String> layout) {
    try {
      return FavoriteLayout.parse(layout);
    } catch (IllegalArgumentException e) {
      throw new InvalidDataException(FA_LAYOUT_INVALID);
    }
  }

  private String organize(Model model, Runnable action) {
    try {
      action.run();
    } catch (ErrorCodeException e) {
      model.addAttribute("errors", errorCodeViewHelper.toViewMessages(e));
    }
    return body(model, true, true);
  }

  /**
   * @param changed whether an action ran: the page behind the dialog shows the favourites as well and
   *                is reloaded when the dialog closes after a change
   */
  private String body(Model model, boolean organize, boolean changed) {
    var favorites = favoriteService.getOwnFavoriteList();
    model.addAttribute("favoriteList", favorites);
    model.addAttribute("ungrouped", favorites.sections().getFirst());
    model.addAttribute("groups", favorites.sections().subList(1, favorites.sections().size()));
    model.addAttribute("customOrder", favorites.sortOrder() == FavoriteSortOrder.CUSTOM);
    model.addAttribute("organize", organize);
    model.addAttribute("changed", changed);
    return BODY;
  }
}
