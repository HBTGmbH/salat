package de.hbt.salat.beta.service;

import static de.hbt.salat.common.util.ClockProvider.now;
import static de.hbt.salat.common.util.ClockProvider.today;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.beta.domain.BetaFeedback;
import de.hbt.salat.beta.domain.BetaParticipation;
import de.hbt.salat.beta.domain.FeedbackState;
import de.hbt.salat.beta.domain.FeedbackTrigger;
import de.hbt.salat.beta.domain.SwitchOffQuestion;
import de.hbt.salat.beta.persistence.BetaFeedbackRepository;
import de.hbt.salat.beta.persistence.BetaParticipationRepository;
import de.hbt.salat.beta.persistence.BetaUsageRepository;
import de.hbt.salat.common.beta.BetaFeature;

/**
 * Asks a person about a beta and stores the answer without them (#1447, ADR-0039).
 *
 * <p>Two questions: how helpful the beta is, once the person has used it often enough
 * ({@link BetaFeature#getFeedbackAfterUses()}) and has had it switched on for at least
 * {@link #MIN_DAYS_ENABLED} days; and why, right after they switched it off. "Later" asks the first
 * again after {@link #POSTPONE_DAYS} days, "do not ask again" never; the second is asked once.
 *
 * <p>What the person answers goes into {@link BetaFeedback}, which has no column for the person;
 * their {@link BetaParticipation} only learns that they answered.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class BetaFeedbackService {

  static final int MIN_DAYS_ENABLED = 7;
  static final int POSTPONE_DAYS = 7;

  private final BetaFeatureRegistry registry;
  private final BetaParticipationRepository participationRepository;
  private final BetaUsageRepository usageRepository;
  private final BetaFeedbackRepository feedbackRepository;
  private final MeasuredPerson measuredPerson;

  /** Whether the page of the beta asks how helpful it is. */
  @Transactional(readOnly = true)
  public boolean isUseFeedbackDue(String featureKey) {
    var feature = registry.find(featureKey);
    var participation = participation(featureKey);
    if (feature.isEmpty() || participation.isEmpty()) {
      return false;
    }
    var p = participation.get();
    return p.isEnabled()
        && isAskable(p)
        && hasBeenOnLongEnough(p)
        && usageRepository.sumUsesWithBeta(featureKey, p.getEmployeeId()) >= feature.get().getFeedbackAfterUses();
  }

  /** The betas the person switched off and has not been asked about yet. */
  @Transactional(readOnly = true)
  public List<SwitchOffQuestion> getSwitchOffQuestions() {
    return measuredPerson.employeeId()
        .map(participationRepository::findSwitchOffPending)
        .orElse(List.of())
        .stream()
        .filter(p -> !p.isEnabled())
        .flatMap(p -> registry.find(p.getFeatureKey()).stream())
        .map(feature -> new SwitchOffQuestion(feature.getKey(), feature.labelKey()))
        .toList();
  }

  /**
   * Stores an answer without the person and marks the question as answered. An answer to a question
   * that is not open — a second click, a forged request — is dropped, so that nobody can flood the
   * evaluation.
   */
  public void submit(String featureKey, FeedbackTrigger trigger, Integer rating, String comment) {
    participation(featureKey).filter(p -> isOpen(p, trigger)).ifPresent(participation -> {
      if (trigger == FeedbackTrigger.USE) {
        participation.setFeedbackState(FeedbackState.ANSWERED);
      } else {
        participation.setSwitchOffPending(false);
      }
      participationRepository.save(participation);
      var cleanRating = rating == null || rating < BetaFeedback.RATING_MIN || rating > BetaFeedback.RATING_MAX
          ? null : rating;
      var cleanComment = comment == null || comment.isBlank() ? null : truncate(comment.strip());
      if (cleanRating == null && cleanComment == null) {
        return;
      }
      var feedback = new BetaFeedback();
      feedback.setFeatureKey(featureKey);
      feedback.setTrigger(trigger);
      feedback.setRating(cleanRating);
      feedback.setCommentText(cleanComment);
      feedback.setIsoWeek(isoWeek(today()));
      feedbackRepository.save(feedback);
    });
  }

  /** "Later": the question how helpful comes back after {@link #POSTPONE_DAYS} days. */
  public void postpone(String featureKey) {
    participation(featureKey).filter(p -> isOpen(p, FeedbackTrigger.USE)).ifPresent(participation -> {
      participation.setFeedbackState(FeedbackState.POSTPONED);
      participation.setFeedbackAskAfter(today().plusDays(POSTPONE_DAYS));
      participationRepository.save(participation);
    });
  }

  /** "Do not ask again" — or, after switching off, skipping the question why. */
  public void decline(String featureKey, FeedbackTrigger trigger) {
    participation(featureKey).filter(p -> isOpen(p, trigger)).ifPresent(participation -> {
      if (trigger == FeedbackTrigger.USE) {
        participation.setFeedbackState(FeedbackState.DECLINED);
      } else {
        participation.setSwitchOffPending(false);
      }
      participationRepository.save(participation);
    });
  }

  static String isoWeek(LocalDate date) {
    return "%d-W%02d".formatted(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
  }

  private Optional<BetaParticipation> participation(String featureKey) {
    return measuredPerson.employeeId().flatMap(employeeId -> participationRepository.findOne(featureKey, employeeId));
  }

  private static boolean isOpen(BetaParticipation participation, FeedbackTrigger trigger) {
    return trigger == FeedbackTrigger.USE ? isAskable(participation) : participation.isSwitchOffPending();
  }

  private static boolean isAskable(BetaParticipation participation) {
    return switch (participation.getFeedbackState()) {
      case OPEN -> true;
      case POSTPONED -> participation.getFeedbackAskAfter() == null
          || !today().isBefore(participation.getFeedbackAskAfter());
      case ANSWERED, DECLINED -> false;
    };
  }

  private static boolean hasBeenOnLongEnough(BetaParticipation participation) {
    return Duration.between(participation.getEnabledAt(), now()).toDays() >= MIN_DAYS_ENABLED;
  }

  private static String truncate(String comment) {
    return comment.length() <= BetaFeedback.COMMENT_MAX_LENGTH ? comment
        : comment.substring(0, BetaFeedback.COMMENT_MAX_LENGTH);
  }
}
