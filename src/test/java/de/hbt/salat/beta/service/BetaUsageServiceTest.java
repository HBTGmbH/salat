package de.hbt.salat.beta.service;

import static de.hbt.salat.beta.service.BetaTestData.EMPLOYEE_ID;
import static de.hbt.salat.beta.service.BetaTestData.EVENT;
import static de.hbt.salat.beta.service.BetaTestData.KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import de.hbt.salat.beta.domain.BetaUsage;
import de.hbt.salat.beta.domain.BetaVariant;
import de.hbt.salat.beta.persistence.BetaEmployeeReferences;
import de.hbt.salat.beta.persistence.BetaUsageRepository;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.testutils.ReferenceTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
@FixedClock("2026-06-25T10:15:30")
class BetaUsageServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 25);

  @Mock
  private BetaFeatureRegistry registry;
  @Mock
  private BetaFeatureService betaFeatureService;
  @Mock
  private BetaUsageRepository usageRepository;
  @Mock
  private BetaEmployeeReferences employeeReferences;
  @Mock
  private MeasuredPerson measuredPerson;
  @Mock
  private PlatformTransactionManager transactionManager;

  @InjectMocks
  private BetaUsageService service;

  @BeforeEach
  void setUp() {
    when(registry.isKnown(KEY)).thenReturn(true);
    when(measuredPerson.employeeId()).thenReturn(Optional.of(EMPLOYEE_ID));
    when(employeeReferences.employee(EMPLOYEE_ID)).thenReturn(ReferenceTestUtils.employeeWithId(EMPLOYEE_ID));
  }

  @Test
  void a_use_with_the_beta_switched_on_counts_on_the_beta_side() {
    when(betaFeatureService.isEnabledForCurrentUser(KEY)).thenReturn(true);
    when(usageRepository.increment(KEY, EVENT, EMPLOYEE_ID, TODAY, BetaVariant.BETA)).thenReturn(1);

    service.count(KEY, EVENT);

    verify(usageRepository).increment(KEY, EVENT, EMPLOYEE_ID, TODAY, BetaVariant.BETA);
    verify(usageRepository, never()).saveAndFlush(any());
  }

  @Test
  void a_use_without_the_beta_counts_for_the_comparison_group() {
    when(betaFeatureService.isEnabledForCurrentUser(KEY)).thenReturn(false);
    when(usageRepository.increment(KEY, EVENT, EMPLOYEE_ID, TODAY, BetaVariant.CLASSIC)).thenReturn(1);

    service.count(KEY, EVENT);

    verify(usageRepository).increment(KEY, EVENT, EMPLOYEE_ID, TODAY, BetaVariant.CLASSIC);
  }

  @Test
  void the_first_use_of_the_day_creates_the_row() {
    when(usageRepository.increment(anyString(), anyString(), anyLong(), any(), any())).thenReturn(0);

    service.count(KEY, EVENT);

    var saved = ArgumentCaptor.forClass(BetaUsage.class);
    verify(usageRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getFeatureKey()).isEqualTo(KEY);
    assertThat(saved.getValue().getEventKey()).isEqualTo(EVENT);
    assertThat(saved.getValue().getUsageDate()).isEqualTo(TODAY);
    assertThat(saved.getValue().getVariant()).isEqualTo(BetaVariant.CLASSIC);
    assertThat(saved.getValue().getUseCount()).isEqualTo(1);
  }

  /** Two requests creating the same row at once: the loser raises the row of the winner. */
  @Test
  void a_row_created_at_the_same_time_is_raised_instead() {
    when(usageRepository.increment(anyString(), anyString(), anyLong(), any(), any())).thenReturn(0);
    when(usageRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_beta_usage"));

    service.count(KEY, EVENT);

    verify(usageRepository, times(2)).increment(KEY, EVENT, EMPLOYEE_ID, TODAY, BetaVariant.CLASSIC);
  }

  /** The module decides its events; any well-formed key is counted. */
  @Test
  void any_well_formed_event_of_the_calling_module_is_counted() {
    when(usageRepository.increment(anyString(), anyString(), anyLong(), any(), any())).thenReturn(1);

    service.count(KEY, "week-strip.hidden");

    verify(usageRepository).increment(KEY, "week-strip.hidden", EMPLOYEE_ID, TODAY, BetaVariant.CLASSIC);
  }

  @Test
  void a_malformed_event_key_is_not_counted() {
    service.count(KEY, "Not an event!");
    service.count(KEY, "");
    service.count(KEY, null);
    service.count(KEY, "x".repeat(65));

    verify(usageRepository, never()).increment(anyString(), anyString(), anyLong(), any(), any());
  }

  @Test
  void an_unknown_beta_is_not_counted() {
    service.count("removed", EVENT);

    verify(usageRepository, never()).increment(anyString(), anyString(), anyLong(), any(), any());
  }

  /** While acting as somebody else, MeasuredPerson has nobody. */
  @Test
  void nothing_is_counted_without_a_measured_person() {
    when(measuredPerson.employeeId()).thenReturn(Optional.empty());

    service.count(KEY, EVENT);

    verify(usageRepository, never()).increment(anyString(), anyString(), anyLong(), any(), any());
  }

  @Test
  void a_failure_never_reaches_the_caller() {
    when(usageRepository.increment(anyString(), anyString(), anyLong(), any(), any()))
        .thenThrow(new IllegalStateException("database gone"));

    assertThatCode(() -> service.count(KEY, EVENT)).doesNotThrowAnyException();
  }
}
