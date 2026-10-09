package de.hbt.salat.beta.service;

import static de.hbt.salat.beta.service.BetaTestData.FEATURE;
import static de.hbt.salat.beta.service.BetaTestData.EVENT;
import static de.hbt.salat.beta.service.BetaTestData.KEY;
import static de.hbt.salat.beta.service.BetaTestData.participation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import de.hbt.salat.beta.domain.BetaEvaluation;
import de.hbt.salat.beta.domain.BetaEvaluation.GroupUsage;
import de.hbt.salat.beta.domain.BetaFeedback;
import de.hbt.salat.beta.domain.BetaParticipation;
import de.hbt.salat.beta.domain.BetaUsageRow;
import de.hbt.salat.beta.domain.BetaVariant;
import de.hbt.salat.beta.domain.FeedbackTrigger;
import de.hbt.salat.beta.persistence.BetaFeedbackRepository;
import de.hbt.salat.beta.persistence.BetaParticipationRepository;
import de.hbt.salat.beta.persistence.BetaUsageRepository;
import de.hbt.salat.common.test.FixedClock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
@FixedClock("2026-06-25T10:15:30")
class BetaEvaluationServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 25, 10, 15, 30);
  /** Thursday of week 26; the page shows weeks 19 to 26. */
  private static final LocalDate THIS_WEEK = LocalDate.of(2026, 6, 23);

  @Mock
  private BetaFeatureRegistry registry;
  @Mock
  private BetaUsageRepository usageRepository;
  @Mock
  private BetaParticipationRepository participationRepository;
  @Mock
  private BetaFeedbackRepository feedbackRepository;

  @InjectMocks
  private BetaEvaluationService service;

  private final List<BetaUsageRow> rows = new ArrayList<>();
  private final List<BetaParticipation> participations = new ArrayList<>();
  private final List<BetaFeedback> feedbacks = new ArrayList<>();

  @BeforeEach
  void setUp() {
    when(registry.all()).thenReturn(List.of(FEATURE));
    when(registry.find(anyString())).thenReturn(Optional.empty());
    when(registry.find(KEY)).thenReturn(Optional.of(FEATURE));
    when(usageRepository.findFeatureKeys()).thenReturn(List.of(KEY));
    when(participationRepository.findFeatureKeys()).thenReturn(List.of());
    when(feedbackRepository.findFeatureKeys()).thenReturn(List.of());
    when(usageRepository.findRows(anyString(), any())).thenReturn(rows);
    when(participationRepository.findAllByFeatureKey(anyString())).thenReturn(participations);
    when(feedbackRepository.findAllByFeatureKey(anyString())).thenReturn(feedbacks);
  }

  @Test
  void the_usage_of_a_week_is_the_mean_per_person_with_its_standard_error() {
    // three people with the beta: 2 + 2 uses, 2, 6 - one row per day, summed per person
    rows.add(row(1, BetaVariant.BETA, 2, THIS_WEEK));
    rows.add(row(1, BetaVariant.BETA, 2, THIS_WEEK.plusDays(1)));
    rows.add(row(2, BetaVariant.BETA, 2, THIS_WEEK));
    rows.add(row(3, BetaVariant.BETA, 6, THIS_WEEK));

    var week = thisWeek(evaluation());

    assertThat(week.isoWeek()).isEqualTo("2026-W26");
    assertThat(week.beta().people()).isEqualTo(3);
    assertThat(week.beta().mean()).isEqualTo(4.0);
    // deviations 0, -2 and 2 around 4: sample standard deviation → sqrt(8/2) = 2; / sqrt(3)
    assertThat(week.beta().standardError()).isCloseTo(2 / Math.sqrt(3), within(1e-9));
  }

  @Test
  void a_group_of_fewer_than_three_people_is_not_shown() {
    rows.add(row(1, BetaVariant.CLASSIC, 3, THIS_WEEK));
    rows.add(row(2, BetaVariant.CLASSIC, 5, THIS_WEEK));

    assertThat(thisWeek(evaluation()).classic()).isEqualTo(GroupUsage.HIDDEN);
  }

  /** The beta module knows no events: an event appears once a module has counted it. */
  @Test
  void a_counted_event_has_eight_weeks_and_no_other_event_appears() {
    assertThat(evaluation().events()).isEmpty();

    rows.add(row(1, BetaVariant.CLASSIC, 1, THIS_WEEK));
    var events = evaluation().events();

    assertThat(events).hasSize(1);
    assertThat(events.getFirst().eventKey()).isEqualTo(EVENT);
    assertThat(events.getFirst().weeks()).hasSize(BetaEvaluationService.WEEKS);
    assertThat(events.getFirst().weeks().getFirst().isoWeek()).isEqualTo("2026-W19");
  }

  @Test
  void the_participation_counts_and_the_switch_off_rate() {
    for (int i = 0; i < 6; i++) {
      participations.add(participation(NOW.minusDays(20)));
    }
    for (int days : new int[] {1, 3, 10}) {
      var off = participation(NOW.minusDays(20));
      off.setDisabledAt(NOW.minusDays(20).plusDays(days));
      participations.add(off);
    }

    var participation = evaluation().participation();

    assertThat(participation.everEnabled()).isEqualTo(9);
    assertThat(participation.enabledNow()).isEqualTo(6);
    assertThat(participation.switchedOff()).isEqualTo(3);
    assertThat(participation.switchOffPercent()).isEqualTo(33);
    assertThat(participation.medianDaysUntilOff()).isEqualTo(3);
  }

  @Test
  void the_participation_of_fewer_than_three_is_not_shown() {
    participations.add(participation(NOW.minusDays(2)));

    var participation = evaluation().participation();

    assertThat(participation.everEnabled()).isNull();
    assertThat(participation.switchOffPercent()).isNull();
  }

  @Test
  void the_answers_show_distribution_mean_and_comments_sorted_by_text() {
    feedbacks.add(feedback(5, "zuletzt geschrieben"));
    feedbacks.add(feedback(4, "als erstes geschrieben"));
    feedbacks.add(feedback(3, null));

    var summary = evaluation().feedback().getFirst();

    assertThat(summary.trigger()).isEqualTo(FeedbackTrigger.USE);
    assertThat(summary.answers()).isEqualTo(3);
    assertThat(summary.ratingCounts()).containsExactly(0, 0, 1, 1, 1);
    assertThat(summary.meanRating()).isEqualTo(4.0);
    assertThat(summary.comments()).containsExactly("als erstes geschrieben", "zuletzt geschrieben");
  }

  @Test
  void fewer_than_three_answers_show_their_number_only() {
    feedbacks.add(feedback(5, "allein"));

    var summary = evaluation().feedback().getFirst();

    assertThat(summary.answers()).isEqualTo(1);
    assertThat(summary.ratingCounts()).isEmpty();
    assertThat(summary.meanRating()).isNull();
    assertThat(summary.comments()).isEmpty();
  }

  @Test
  void an_ended_beta_stays_under_its_key_while_its_rows_are_there() {
    when(registry.all()).thenReturn(List.of());
    when(usageRepository.findFeatureKeys()).thenReturn(List.of("ended-beta"));

    var evaluations = service.getEvaluations();

    assertThat(evaluations).hasSize(1);
    assertThat(evaluations.getFirst().featureKey()).isEqualTo("ended-beta");
    assertThat(evaluations.getFirst().isEnded()).isTrue();
  }

  private BetaEvaluation evaluation() {
    var evaluations = service.getEvaluations();
    assertThat(evaluations).hasSize(1);
    return evaluations.getFirst();
  }

  private static BetaEvaluation.WeekUsage thisWeek(BetaEvaluation evaluation) {
    return evaluation.events().getFirst().weeks().getLast();
  }

  private static BetaUsageRow row(long employeeId, BetaVariant variant, int count, LocalDate date) {
    return new BetaUsageRow(EVENT, employeeId, date, variant, count);
  }

  private static BetaFeedback feedback(Integer rating, String comment) {
    var feedback = new BetaFeedback();
    feedback.setFeatureKey(KEY);
    feedback.setTrigger(FeedbackTrigger.USE);
    feedback.setRating(rating);
    feedback.setCommentText(comment);
    feedback.setIsoWeek("2026-W26");
    return feedback;
  }
}
