package de.hbt.salat.favorites.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static de.hbt.salat.common.exception.ErrorCode.FA_GROUP_NAME_TAKEN;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ui.ExtendedModelMap;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.favorites.domain.FavoriteList;
import de.hbt.salat.favorites.domain.FavoriteSection;
import de.hbt.salat.favorites.domain.FavoriteSortOrder;
import de.hbt.salat.favorites.service.FavoriteService;

/**
 * The dialog "Favoriten" (#1414) opens to pick from; arranging is a mode of its own, and every action
 * of that mode answers in it. A refusal stands in the dialog instead of an error page.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class FavoriteDialogControllerTest {

  @Mock
  private FavoriteService favoriteService;
  @Mock
  private ErrorCodeViewHelper errorCodeViewHelper;
  @InjectMocks
  private FavoriteDialogController controller;

  @BeforeEach
  void aListWithOneGroup() {
    when(favoriteService.getOwnFavoriteList()).thenReturn(new FavoriteList(FavoriteSortOrder.RECENT, List.of(
        new FavoriteSection(null, null, List.of()), new FavoriteSection(5L, "Wartung", List.of()))));
    when(errorCodeViewHelper.toViewMessages(any())).thenReturn(List.of());
  }

  @Test
  void it_opens_to_pick_from_and_reports_no_change() {
    var model = new ExtendedModelMap();

    var view = controller.show(false, model);

    assertThat(view).isEqualTo(FavoriteDialogController.BODY);
    assertThat(model.get("organize")).isEqualTo(false);
    assertThat(model.get("changed")).isEqualTo(false);
    assertThat(model.get("groups")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).hasSize(1);
  }

  @Test
  void an_action_answers_in_the_arranging_mode_and_marks_the_change() {
    var model = new ExtendedModelMap();

    controller.createGroup("Projekt", model);

    verify(favoriteService).createGroup("Projekt");
    assertThat(model.get("organize")).isEqualTo(true);
    assertThat(model.get("changed")).isEqualTo(true);
  }

  @Test
  void a_refusal_is_shown_in_the_dialog() {
    doThrow(new BusinessRuleException(FA_GROUP_NAME_TAKEN, "Wartung")).when(favoriteService).createGroup("Wartung");
    var model = new ExtendedModelMap();

    var view = controller.createGroup("Wartung", model);

    assertThat(view).isEqualTo(FavoriteDialogController.BODY);
    assertThat(model.containsAttribute("errors")).isTrue();
  }

  @Test
  void a_broken_arrangement_is_refused_before_the_service() {
    var model = new ExtendedModelMap();

    controller.arrange(List.of("favorite:1"), model);

    verify(favoriteService, never()).arrange(any());
    assertThat(model.containsAttribute("errors")).isTrue();
  }
}
