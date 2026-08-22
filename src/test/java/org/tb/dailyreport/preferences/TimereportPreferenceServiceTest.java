package org.tb.dailyreport.preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tb.dailyreport.preferences.DurationInputMode.BEGIN_END;
import static org.tb.dailyreport.preferences.DurationInputMode.DURATION;
import static org.tb.dailyreport.preferences.DurationInputMode.REMEMBER;
import static org.tb.dailyreport.preferences.TimereportPreferences.MODULE_KEY;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tb.settings.service.UserPreferenceService;

/**
 * Learning the entry mode of the booking form from the last created booking (#844).
 */
@ExtendWith(MockitoExtension.class)
class TimereportPreferenceServiceTest {

  @Mock
  private UserPreferenceService userPreferenceService;

  private TimereportPreferenceService service;

  @BeforeEach
  void setUp() {
    service = new TimereportPreferenceService(userPreferenceService);
  }

  @Test
  void booking_with_begin_end_is_remembered() {
    stored(TimereportPreferences.defaults());

    service.rememberDurationMode("beginEnd");

    assertThat(saved()).isEqualTo(new TimereportPreferences(null, REMEMBER, BEGIN_END));
  }

  @Test
  void the_favorite_suborder_is_kept_while_remembering() {
    stored(new TimereportPreferences(7L, REMEMBER, DURATION));

    service.rememberDurationMode("beginEnd");

    assertThat(saved().favoriteSuborderId()).isEqualTo(7L);
  }

  @Test
  void the_first_booking_ever_is_remembered_as_well() {
    stored(TimereportPreferences.defaults());

    service.rememberDurationMode("duration");

    assertThat(saved().lastUsedDurationMode()).isEqualTo(DURATION);
  }

  @Test
  void booking_in_the_already_remembered_mode_writes_nothing() {
    stored(new TimereportPreferences(null, REMEMBER, BEGIN_END));

    service.rememberDurationMode("beginEnd");

    verify(userPreferenceService, never()).saveModuleSettings(anyString(), anyMap());
  }

  @Test
  void an_explicitly_chosen_mode_is_not_overwritten_by_a_single_booking() {
    stored(new TimereportPreferences(null, DURATION, DURATION));

    service.rememberDurationMode("beginEnd");

    verify(userPreferenceService, never()).saveModuleSettings(anyString(), anyMap());
  }

  @Test
  void toggling_the_favorite_keeps_the_entry_mode() {
    stored(new TimereportPreferences(null, BEGIN_END, BEGIN_END));

    service.toggleFavoriteSuborder(3L);

    assertThat(saved()).isEqualTo(new TimereportPreferences(3L, BEGIN_END, BEGIN_END));
  }

  private void stored(TimereportPreferences preferences) {
    when(userPreferenceService.getModuleSettings(MODULE_KEY)).thenReturn(preferences.toMap());
  }

  private TimereportPreferences saved() {
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(userPreferenceService).saveModuleSettings(eq(MODULE_KEY), captor.capture());
    return TimereportPreferences.from(captor.getValue());
  }

}
