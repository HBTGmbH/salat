package de.hbt.salat.beta.service;

import static de.hbt.salat.beta.service.BetaTestData.FEATURE;
import static de.hbt.salat.beta.service.BetaTestData.EMPLOYEE_ID;
import static de.hbt.salat.beta.service.BetaTestData.KEY;
import static de.hbt.salat.beta.service.BetaTestData.participation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import de.hbt.salat.beta.domain.BetaFeedback;
import de.hbt.salat.beta.domain.BetaParticipation;
import de.hbt.salat.beta.domain.FeedbackState;
import de.hbt.salat.beta.domain.FeedbackTrigger;
import de.hbt.salat.beta.domain.SwitchOffQuestion;
import de.hbt.salat.beta.persistence.BetaFeedbackRepository;
import de.hbt.salat.beta.persistence.BetaParticipationRepository;
import de.hbt.salat.beta.persistence.BetaUsageRepository;
import de.hbt.salat.common.test.FixedClock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
@FixedClock("2026-06-25T10:15:30")
class BetaFeedbackServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 25, 10, 15, 30);

  @Mock
  private BetaFeatureRegistry registry;
  @Mock
  private BetaParticipationRepository participationRepository;
  @Mock
  private BetaUsageRepository usageRepository;
  @Mock
  private BetaFeedbackRepository feedbackRepository;
  @Mock
  private MeasuredPerson measuredPerson;

  @InjectMocks
  private BetaFeedbackService service;

  private BetaParticipation participation;

  @BeforeEach
  void setUp() {
    when(registry.find(KEY)).thenReturn(Optional.of(FEATURE));
    when(measuredPerson.employeeId()).thenReturn(Optional.of(EMPLOYEE_ID));
    participation = participation(NOW.minusDays(BetaFeedbackService.MIN_DAYS_ENABLED));
    when(participationRepository.findOne(KEY, EMPLOYEE_ID)).thenAnswer(invocation -> Optional.ofNullable(participation));
    uses(FEATURE.getFeedbackAfterUses());
  }

  @Nested
  class The_question_how_helpful {

    @Test
    void is_due_after_enough_uses_and_days() {
      assertThat(service.isUseFeedbackDue(KEY)).isTrue();
    }

    @Test
    void waits_for_enough_uses() {
      uses(FEATURE.getFeedbackAfterUses() - 1);

      assertThat(service.isUseFeedbackDue(KEY)).isFalse();
    }

    @Test
    void waits_a_week_after_switching_on() {
      participation.setEnabledAt(NOW.minusDays(BetaFeedbackService.MIN_DAYS_ENABLED - 1));

      assertThat(service.isUseFeedbackDue(KEY)).isFalse();
    }

    @Test
    void is_not_asked_with_the_beta_switched_off() {
      participation.setDisabledAt(NOW.minusHours(1));

      assertThat(service.isUseFeedbackDue(KEY)).isFalse();
    }

    @Test
    void comes_back_a_week_after_later() {
      service.postpone(KEY);

      assertThat(participation.getFeedbackState()).isEqualTo(FeedbackState.POSTPONED);
      assertThat(participation.getFeedbackAskAfter()).isEqualTo(LocalDate.of(2026, 7, 2));
      assertThat(service.isUseFeedbackDue(KEY)).isFalse();

      participation.setFeedbackAskAfter(LocalDate.of(2026, 6, 25));
      assertThat(service.isUseFeedbackDue(KEY)).isTrue();
    }

    @Test
    void is_never_asked_again_after_do_not_ask() {
      service.decline(KEY, FeedbackTrigger.USE);

      assertThat(participation.getFeedbackState()).isEqualTo(FeedbackState.DECLINED);
      assertThat(service.isUseFeedbackDue(KEY)).isFalse();
    }

    @Test
    void is_not_asked_without_a_participation() {
      participation = null;

      assertThat(service.isUseFeedbackDue(KEY)).isFalse();
    }
  }

  @Nested
  class An_answer {

    @Test
    void is_stored_without_the_person_and_with_the_week_only() {
      service.submit(KEY, FeedbackTrigger.USE, 4, "  hilft beim Buchen  ");

      var saved = ArgumentCaptor.forClass(BetaFeedback.class);
      verify(feedbackRepository).save(saved.capture());
      assertThat(saved.getValue().getFeatureKey()).isEqualTo(KEY);
      assertThat(saved.getValue().getTrigger()).isEqualTo(FeedbackTrigger.USE);
      assertThat(saved.getValue().getRating()).isEqualTo(4);
      assertThat(saved.getValue().getCommentText()).isEqualTo("hilft beim Buchen");
      assertThat(saved.getValue().getIsoWeek()).isEqualTo("2026-W26");
      assertThat(participation.getFeedbackState()).isEqualTo(FeedbackState.ANSWERED);
      assertThat(service.isUseFeedbackDue(KEY)).isFalse();
    }

    @Test
    void drops_a_rating_outside_the_scale() {
      service.submit(KEY, FeedbackTrigger.USE, 9, "zu gut");

      var saved = ArgumentCaptor.forClass(BetaFeedback.class);
      verify(feedbackRepository).save(saved.capture());
      assertThat(saved.getValue().getRating()).isNull();
    }

    @Test
    void without_rating_and_comment_closes_the_question_but_stores_nothing() {
      service.submit(KEY, FeedbackTrigger.USE, null, " ");

      verify(feedbackRepository, never()).save(any());
      assertThat(participation.getFeedbackState()).isEqualTo(FeedbackState.ANSWERED);
    }

    /** A second click or a forged request must not add to the evaluation. */
    @Test
    void to_a_question_that_is_not_open_is_dropped() {
      participation.setFeedbackState(FeedbackState.ANSWERED);

      service.submit(KEY, FeedbackTrigger.USE, 5, "noch einmal");
      service.submit(KEY, FeedbackTrigger.SWITCH_OFF, 1, "nie gefragt");

      verify(feedbackRepository, never()).save(any());
    }

    @Test
    void why_after_switching_off_closes_that_question() {
      participation.setDisabledAt(NOW);
      participation.setSwitchOffPending(true);

      service.submit(KEY, FeedbackTrigger.SWITCH_OFF, null, "zu unruhig");

      verify(feedbackRepository).save(any());
      assertThat(participation.isSwitchOffPending()).isFalse();
    }
  }

  @Test
  void the_settings_page_asks_about_the_betas_switched_off_last() {
    participation.setDisabledAt(NOW);
    participation.setSwitchOffPending(true);
    var ended = participation(NOW.minusDays(9));
    ended.setFeatureKey("removed-beta");
    ended.setDisabledAt(NOW);
    ended.setSwitchOffPending(true);
    when(registry.find("removed-beta")).thenReturn(Optional.empty());
    when(participationRepository.findSwitchOffPending(EMPLOYEE_ID)).thenReturn(List.of(participation, ended));

    assertThat(service.getSwitchOffQuestions())
        .containsExactly(new SwitchOffQuestion(KEY, FEATURE.labelKey()));
  }

  @Test
  void the_iso_week_crosses_the_year_as_iso_does() {
    assertThat(BetaFeedbackService.isoWeek(LocalDate.of(2027, 1, 1))).isEqualTo("2026-W53");
    assertThat(BetaFeedbackService.isoWeek(LocalDate.of(2026, 10, 9))).isEqualTo("2026-W41");
  }

  private void uses(long count) {
    when(usageRepository.sumUsesWithBeta(KEY, EMPLOYEE_ID)).thenReturn(count);
  }
}
