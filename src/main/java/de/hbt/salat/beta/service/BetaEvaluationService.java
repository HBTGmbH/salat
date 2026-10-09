package de.hbt.salat.beta.service;

import static de.hbt.salat.beta.domain.BetaEvaluation.MIN_PEOPLE;
import static de.hbt.salat.common.util.ClockProvider.today;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingInt;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.beta.domain.BetaEvaluation;
import de.hbt.salat.beta.domain.BetaEvaluation.EventUsage;
import de.hbt.salat.beta.domain.BetaEvaluation.FeedbackSummary;
import de.hbt.salat.beta.domain.BetaEvaluation.GroupUsage;
import de.hbt.salat.beta.domain.BetaEvaluation.Participation;
import de.hbt.salat.beta.domain.BetaEvaluation.WeekUsage;
import de.hbt.salat.beta.domain.BetaFeedback;
import de.hbt.salat.beta.domain.BetaParticipation;
import de.hbt.salat.beta.domain.BetaUsageRow;
import de.hbt.salat.beta.domain.BetaVariant;
import de.hbt.salat.beta.domain.FeedbackTrigger;
import de.hbt.salat.beta.persistence.BetaFeedbackRepository;
import de.hbt.salat.beta.persistence.BetaParticipationRepository;
import de.hbt.salat.beta.persistence.BetaUsageRepository;
import de.hbt.salat.common.beta.BetaFeature;

/**
 * Sums up what was measured about the betas (#1447), for management. The rows name people; what
 * leaves this service does not — every number is a sum, and a number behind fewer than
 * {@link BetaEvaluation#MIN_PEOPLE} people is left out (ADR-0039).
 *
 * <p>A beta that has ended stays here, under its key, as long as its rows are there; the ticket
 * that ends it records the numbers and then deletes the rows.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Authorized(requiresManager = true)
public class BetaEvaluationService {

  /** How many weeks of use the page shows, the current one included. */
  static final int WEEKS = 8;

  private final BetaFeatureRegistry registry;
  private final BetaUsageRepository usageRepository;
  private final BetaParticipationRepository participationRepository;
  private final BetaFeedbackRepository feedbackRepository;

  public List<BetaEvaluation> getEvaluations() {
    var keys = new LinkedHashSet<String>();
    registry.all().forEach(feature -> keys.add(feature.getKey()));
    // then those with rows only, which have ended
    var measuredKeys = new TreeSet<String>();
    measuredKeys.addAll(usageRepository.findFeatureKeys());
    measuredKeys.addAll(participationRepository.findFeatureKeys());
    measuredKeys.addAll(feedbackRepository.findFeatureKeys());
    keys.addAll(measuredKeys);
    return keys.stream().map(this::evaluate).toList();
  }

  private BetaEvaluation evaluate(String featureKey) {
    var feature = registry.find(featureKey);
    var firstWeek = today().with(DayOfWeek.MONDAY).minusWeeks(WEEKS - 1);
    return new BetaEvaluation(featureKey,
        feature.map(BetaFeature::labelKey).orElse(null),
        participation(participationRepository.findAllByFeatureKey(featureKey)),
        events(usageRepository.findRows(featureKey, firstWeek), firstWeek),
        feedback(feedbackRepository.findAllByFeatureKey(featureKey)));
  }

  static Participation participation(List<BetaParticipation> participations) {
    int everEnabled = participations.size();
    int enabledNow = (int) participations.stream().filter(BetaParticipation::isEnabled).count();
    var off = participations.stream().filter(p -> !p.isEnabled()).toList();
    var daysUntilOff = off.stream()
        .map(p -> Duration.between(p.getEnabledAt(), p.getDisabledAt()).toDays())
        .sorted()
        .toList();
    return new Participation(shown(everEnabled), shown(enabledNow), shown(off.size()),
        everEnabled >= MIN_PEOPLE && off.size() >= MIN_PEOPLE ? Math.round(100f * off.size() / everEnabled) : null,
        daysUntilOff.size() >= MIN_PEOPLE ? (int) (long) daysUntilOff.get(daysUntilOff.size() / 2) : null);
  }

  /** The events counted in the weeks shown, by name; the beta module knows no others. */
  static List<EventUsage> events(List<BetaUsageRow> rows, LocalDate firstWeek) {
    var eventKeys = new TreeSet<String>();
    rows.forEach(row -> eventKeys.add(row.eventKey()));
    var events = new ArrayList<EventUsage>();
    for (var eventKey : eventKeys) {
      var weeks = new ArrayList<WeekUsage>();
      for (int week = 0; week < WEEKS; week++) {
        var monday = firstWeek.plusWeeks(week);
        var inWeek = rows.stream()
            .filter(row -> row.eventKey().equals(eventKey))
            .filter(row -> !row.usageDate().isBefore(monday) && row.usageDate().isBefore(monday.plusWeeks(1)))
            .toList();
        weeks.add(new WeekUsage(BetaFeedbackService.isoWeek(monday),
            group(inWeek, BetaVariant.BETA), group(inWeek, BetaVariant.CLASSIC)));
      }
      events.add(new EventUsage(eventKey, weeks));
    }
    return events;
  }

  /** The uses per person of one side in one week; hidden below {@link BetaEvaluation#MIN_PEOPLE}. */
  static GroupUsage group(List<BetaUsageRow> rowsOfWeek, BetaVariant variant) {
    Map<Long, Integer> usesPerPerson = rowsOfWeek.stream()
        .filter(row -> row.variant() == variant)
        .collect(groupingBy(BetaUsageRow::employeeId, summingInt(BetaUsageRow::useCount)));
    if (usesPerPerson.size() < MIN_PEOPLE) {
      return GroupUsage.HIDDEN;
    }
    var values = usesPerPerson.values().stream().map(Integer::doubleValue).toList();
    return new GroupUsage(values.size(), mean(values), standardError(values));
  }

  static List<FeedbackSummary> feedback(List<BetaFeedback> feedbacks) {
    var summaries = new ArrayList<FeedbackSummary>();
    for (var trigger : FeedbackTrigger.values()) {
      var answers = feedbacks.stream().filter(f -> f.getTrigger() == trigger).toList();
      if (answers.isEmpty()) {
        continue;
      }
      var ratings = answers.stream().map(BetaFeedback::getRating).filter(Objects::nonNull)
          .map(Integer::doubleValue).toList();
      var ratingCounts = List.<Integer>of();
      var comments = List.<String>of();
      if (answers.size() >= MIN_PEOPLE) {
        var counted = new int[BetaFeedback.RATING_MAX];
        ratings.forEach(rating -> counted[rating.intValue() - BetaFeedback.RATING_MIN]++);
        ratingCounts = Arrays.stream(counted).boxed().toList();
        comments = answers.stream().map(BetaFeedback::getCommentText).filter(Objects::nonNull).sorted().toList();
      }
      var enoughRatings = ratings.size() >= MIN_PEOPLE;
      summaries.add(new FeedbackSummary(trigger, answers.size(), ratingCounts,
          enoughRatings ? mean(ratings) : null, enoughRatings ? standardError(ratings) : null, comments));
    }
    return summaries;
  }

  static double mean(List<Double> values) {
    return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
  }

  /** The standard error of the mean, from the sample standard deviation. */
  static double standardError(List<Double> values) {
    int n = values.size();
    if (n < 2) {
      return 0;
    }
    double mean = mean(values);
    double variance = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / (n - 1);
    return Math.sqrt(variance / n);
  }

  private static Integer shown(int people) {
    return people >= MIN_PEOPLE ? people : null;
  }
}
