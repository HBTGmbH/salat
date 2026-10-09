package de.hbt.salat.settingseditor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static de.hbt.salat.common.exception.ErrorCode.FA_LIST_SIZE_INVALID;

import java.util.ArrayList;
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
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import de.hbt.salat.beta.service.BetaFeatureService;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.dailyreport.preferences.DailyPreferenceService;
import de.hbt.salat.dailyreport.preferences.DailySidebarPreferenceService;
import de.hbt.salat.dailyreport.preferences.DailySidebarPreferences;
import de.hbt.salat.dailyreport.preferences.FavoriteShortListOrder;
import de.hbt.salat.dailyreport.preferences.TimereportPreferenceService;
import de.hbt.salat.dailyreport.preferences.TimereportPreferences;
import de.hbt.salat.dailyreport.preferences.WeekStripPlacement;
import de.hbt.salat.employee.preferences.EmployeePreferenceService;
import de.hbt.salat.favorites.service.FavoriteService;
import de.hbt.salat.settings.service.UiPreferenceService;
import de.hbt.salat.settings.web.LocaleSyncInterceptor;

/**
 * The settings of the beta „Favoriten zuerst“ (#1442): the place of the week, what card and dropdown
 * follow, and how many favourites the short list shows (#1414). They are stored only while the beta
 * is on; switched off, the page neither shows nor sends them, and what was stored stays. A number out
 * of range is refused by the favourites module, and the form then saves nothing at all.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class SettingsFavoritesFirstTest {

  @Mock
  private FavoriteService favoriteService;
  @Mock
  private ErrorCodeViewHelper errorCodeViewHelper;
  @Mock
  private UiPreferenceService uiPreferenceService;
  @Mock
  private DailyPreferenceService dailyPreferenceService;
  @Mock
  private TimereportPreferenceService timereportPreferenceService;
  @Mock
  private DailySidebarPreferenceService dailySidebarPreferenceService;
  @Mock
  private EmployeePreferenceService employeePreferenceService;
  @Mock
  private BetaFeatureService betaFeatureService;
  @Mock
  private LocaleSyncInterceptor localeSyncInterceptor;
  @Mock
  private MessageSourceAccessor messages;
  @InjectMocks
  private SettingsController controller;

  @BeforeEach
  void setUp() {
    when(timereportPreferenceService.getForCurrentUser()).thenReturn(TimereportPreferences.defaults());
  }

  @Test
  void with_the_beta_on_its_settings_are_stored() {
    var form = formWithBeta();
    form.setWeekStripPlacement("hidden");
    form.setFavoriteShortListOrder("recent");
    form.setFavoriteListSize(25);

    store(form);

    verify(favoriteService).setListSize(25);
    verify(dailySidebarPreferenceService).saveForCurrentUser(
        new DailySidebarPreferences(WeekStripPlacement.HIDDEN, FavoriteShortListOrder.RECENTLY_USED));
    verify(betaFeatureService).saveForCurrentUser(List.of("favoritesfirst"));
  }

  /** The form offers no other value; a forged one gets the default of the beta. */
  @Test
  void a_value_the_form_does_not_offer_falls_back_to_the_default() {
    var form = formWithBeta();
    form.setWeekStripPlacement("sideways");
    form.setFavoriteShortListOrder(null);

    store(form);

    verify(dailySidebarPreferenceService).saveForCurrentUser(DailySidebarPreferences.defaults());
  }

  /** Switched off, the fieldset is disabled and sends nothing; nothing stored is overwritten. */
  @Test
  void with_the_beta_off_its_settings_are_left_as_they_are() {
    var form = new SettingsController.SettingsForm();
    form.setBetaFeatures(new ArrayList<>());
    form.setFavoriteListSize(null);
    form.setWeekStripPlacement(null);
    form.setFavoriteShortListOrder(null);

    var view = store(form);

    assertThat(view).isEqualTo("redirect:/settings");
    verify(favoriteService, never()).setListSize(anyInt());
    verify(dailySidebarPreferenceService, never()).saveForCurrentUser(any());
    verify(uiPreferenceService).saveLocaleForCurrentUser(any());
  }

  @Test
  void a_number_out_of_range_saves_nothing_and_says_why() {
    doThrow(new InvalidDataException(FA_LIST_SIZE_INVALID, 1, 50)).when(favoriteService).setListSize(0);
    when(errorCodeViewHelper.toViewMessages(any()))
        .thenReturn(List.of(new ErrorCodeViewHelper.ViewMessage("errorcode.fa.0008", new Object[] {1, 50}, "zwischen 1 und 50")));
    var form = formWithBeta();
    form.setFavoriteListSize(0);
    var redirect = new RedirectAttributesModelMap();

    var view = controller.store(form, redirect, new MockHttpServletRequest(), new MockHttpServletResponse());

    assertThat(view).isEqualTo("redirect:/settings");
    assertThat(redirect.getFlashAttributes().get("toastError")).asString().contains("zwischen 1 und 50");
    verify(uiPreferenceService, never()).saveLocaleForCurrentUser(anyString());
    verify(dailyPreferenceService, never()).saveForCurrentUser(any());
    verify(dailySidebarPreferenceService, never()).saveForCurrentUser(any());
  }

  private static SettingsController.SettingsForm formWithBeta() {
    var form = new SettingsController.SettingsForm();
    form.setBetaFeatures(new ArrayList<>(List.of("favoritesfirst")));
    return form;
  }

  private String store(SettingsController.SettingsForm form) {
    return controller.store(form, new RedirectAttributesModelMap(), new MockHttpServletRequest(),
        new MockHttpServletResponse());
  }
}
