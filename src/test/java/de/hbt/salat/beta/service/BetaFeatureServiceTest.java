package de.hbt.salat.beta.service;

import static de.hbt.salat.beta.service.BetaTestData.EMPLOYEE_ID;
import static de.hbt.salat.beta.service.BetaTestData.KEY;
import static de.hbt.salat.beta.service.BetaTestData.participation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import de.hbt.salat.beta.domain.BetaFeatures;
import de.hbt.salat.beta.domain.BetaParticipation;
import de.hbt.salat.beta.persistence.BetaEmployeeReferences;
import de.hbt.salat.beta.persistence.BetaParticipationRepository;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.settings.service.UserPreferenceService;
import de.hbt.salat.testutils.ReferenceTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
@FixedClock("2026-06-25T10:15:30")
class BetaFeatureServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 25, 10, 15, 30);

  @Mock
  private UserPreferenceService userPreferenceService;
  @Mock
  private BetaCatalog catalog;
  @Mock
  private BetaParticipationRepository participationRepository;
  @Mock
  private BetaEmployeeReferences employeeReferences;
  @Mock
  private MeasuredPerson measuredPerson;

  @InjectMocks
  private BetaFeatureService service;

  @BeforeEach
  void setUp() {
    when(catalog.isKnown(anyString())).thenAnswer(invocation -> KEY.equals(invocation.getArgument(0)));
    when(measuredPerson.employeeId()).thenReturn(Optional.of(EMPLOYEE_ID));
    when(employeeReferences.employee(EMPLOYEE_ID)).thenReturn(ReferenceTestUtils.employeeWithId(EMPLOYEE_ID));
    storedSwitches(List.of());
  }

  @Test
  void switching_on_stores_the_key_and_records_the_participation() {
    when(participationRepository.findOne(KEY, EMPLOYEE_ID)).thenReturn(Optional.empty());

    service.saveForCurrentUser(List.of(KEY, "removed-beta"));

    verify(userPreferenceService).saveModuleSettings(BetaFeatures.MODULE_KEY, Map.of("enabled", List.of(KEY)));
    var saved = ArgumentCaptor.forClass(BetaParticipation.class);
    verify(participationRepository).save(saved.capture());
    assertThat(saved.getValue().getFirstEnabledAt()).isEqualTo(NOW);
    assertThat(saved.getValue().getEnabledAt()).isEqualTo(NOW);
    assertThat(saved.getValue().isEnabled()).isTrue();
  }

  @Test
  void switching_on_again_keeps_the_first_time_and_drops_an_open_question_why() {
    var earlier = participation(NOW.minusDays(30));
    earlier.setDisabledAt(NOW.minusDays(10));
    earlier.setSwitchOffPending(true);
    when(participationRepository.findOne(KEY, EMPLOYEE_ID)).thenReturn(Optional.of(earlier));

    service.saveForCurrentUser(List.of(KEY));

    assertThat(earlier.getFirstEnabledAt()).isEqualTo(NOW.minusDays(30));
    assertThat(earlier.getEnabledAt()).isEqualTo(NOW);
    assertThat(earlier.isSwitchOffPending()).isFalse();
    assertThat(earlier.isEnabled()).isTrue();
  }

  @Test
  void switching_off_records_the_time_and_asks_why_next() {
    storedSwitches(List.of(KEY));
    var participation = participation(NOW.minusDays(3));
    when(participationRepository.findOne(KEY, EMPLOYEE_ID)).thenReturn(Optional.of(participation));

    service.saveForCurrentUser(List.of());

    verify(userPreferenceService).saveModuleSettings(BetaFeatures.MODULE_KEY, Map.of());
    assertThat(participation.getDisabledAt()).isEqualTo(NOW);
    assertThat(participation.isSwitchOffPending()).isTrue();
    assertThat(participation.isEnabled()).isFalse();
  }

  @Test
  void saving_unchanged_switches_records_nothing() {
    storedSwitches(List.of(KEY));

    service.saveForCurrentUser(List.of(KEY));

    verify(participationRepository, never()).save(any());
  }

  /** The switch is the real login's preference; acting as somebody else, nothing is recorded. */
  @Test
  void a_switch_while_acting_as_somebody_else_is_stored_but_not_recorded() {
    when(measuredPerson.employeeId()).thenReturn(Optional.empty());

    service.saveForCurrentUser(List.of(KEY));

    verify(userPreferenceService).saveModuleSettings(eq(BetaFeatures.MODULE_KEY), any());
    verify(participationRepository, never()).save(any());
  }

  @Test
  void the_activation_link_ignores_an_unknown_key_and_an_enabled_beta() {
    service.enableForCurrentUser("removed-beta");
    storedSwitches(List.of(KEY));
    service.enableForCurrentUser(KEY);

    verify(userPreferenceService, never()).saveModuleSettings(anyString(), any());
  }

  @Test
  void the_activation_link_switches_on_and_records() {
    when(participationRepository.findOne(KEY, EMPLOYEE_ID)).thenReturn(Optional.empty());

    service.enableForCurrentUser(KEY);

    verify(userPreferenceService).saveModuleSettings(BetaFeatures.MODULE_KEY, Map.of("enabled", List.of(KEY)));
    verify(participationRepository).save(any());
  }

  private void storedSwitches(List<String> keys) {
    when(userPreferenceService.getModuleSettings(BetaFeatures.MODULE_KEY))
        .thenReturn(keys.isEmpty() ? Map.of() : Map.of("enabled", keys));
  }
}
