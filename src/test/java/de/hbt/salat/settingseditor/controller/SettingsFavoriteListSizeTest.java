package de.hbt.salat.settingseditor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static de.hbt.salat.common.exception.ErrorCode.FA_LIST_SIZE_INVALID;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.dailyreport.preferences.DailyPreferenceService;
import de.hbt.salat.favorites.service.FavoriteService;
import de.hbt.salat.settings.service.UiPreferenceService;

/**
 * How many favourites the short list shows is a personal setting (#1414). A value out of range is
 * refused by the favourites module, and the form then saves nothing at all.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class SettingsFavoriteListSizeTest {

  @Mock
  private FavoriteService favoriteService;
  @Mock
  private ErrorCodeViewHelper errorCodeViewHelper;
  @Mock
  private UiPreferenceService uiPreferenceService;
  @Mock
  private DailyPreferenceService dailyPreferenceService;
  @InjectMocks
  private SettingsController controller;

  @Test
  void a_number_out_of_range_saves_nothing_and_says_why() {
    doThrow(new InvalidDataException(FA_LIST_SIZE_INVALID, 1, 50)).when(favoriteService).setListSize(0);
    when(errorCodeViewHelper.toViewMessages(any()))
        .thenReturn(List.of(new ErrorCodeViewHelper.ViewMessage("errorcode.fa.0008", new Object[] {1, 50}, "zwischen 1 und 50")));
    var form = new SettingsController.SettingsForm();
    form.setFavoriteListSize(0);
    var redirect = new RedirectAttributesModelMap();

    var view = controller.store(form, redirect, new MockHttpServletRequest(), new MockHttpServletResponse());

    assertThat(view).isEqualTo("redirect:/settings");
    assertThat(redirect.getFlashAttributes().get("toastError")).asString().contains("zwischen 1 und 50");
    verify(uiPreferenceService, never()).saveLocaleForCurrentUser(anyString());
    verify(dailyPreferenceService, never()).saveForCurrentUser(any());
  }
}
