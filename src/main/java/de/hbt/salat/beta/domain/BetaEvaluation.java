package de.hbt.salat.beta.domain;

import java.util.List;

/**
 * What the evaluation page shows about one beta (#1447): sums only, never a person. A value behind
 * fewer than {@link #MIN_PEOPLE} people is {@code null} — the page shows a dash instead.
 *
 * @param featureKey the key of the beta
 * @param labelKey   the message key of its name; {@code null} once the beta has ended and only its
 *                   rows are left
 */
public record BetaEvaluation(String featureKey, String labelKey, Participation participation,
                             List<EventUsage> events, List<FeedbackSummary> feedback) {

  /** Below this many people a value is not shown (ADR-0039). */
  public static final int MIN_PEOPLE = 3;

  public BetaEvaluation {
    events = List.copyOf(events);
    feedback = List.copyOf(feedback);
  }

  public boolean isEnded() {
    return labelKey == null;
  }

  /**
   * @param everEnabled          how many switched it on at least once
   * @param enabledNow           how many have it on now
   * @param switchedOff          how many switched it off again and have it off now
   * @param switchOffPercent     {@code switchedOff} of {@code everEnabled}, in percent
   * @param medianDaysUntilOff   the median of the days from the last switching on to switching off,
   *                             over those who have it off now
   */
  public record Participation(Integer everEnabled, Integer enabledNow, Integer switchedOff,
                              Integer switchOffPercent, Integer medianDaysUntilOff) {
  }

  /** One counted event, week by week, the oldest week first. */
  public record EventUsage(String eventKey, List<WeekUsage> weeks) {

    public EventUsage {
      weeks = List.copyOf(weeks);
    }
  }

  /** One week of an event, with the beta and without it. */
  public record WeekUsage(String isoWeek, GroupUsage beta, GroupUsage classic) {
  }

  /**
   * The uses per person in a week, over the people who used the event in that week at all.
   *
   * @param people        how many people; {@code null} below {@link #MIN_PEOPLE}
   * @param mean          the mean uses per person
   * @param standardError the standard error of that mean
   */
  public record GroupUsage(Integer people, Double mean, Double standardError) {

    public static final GroupUsage HIDDEN = new GroupUsage(null, null, null);
  }

  /**
   * The answers to one of the two questions.
   *
   * @param answers       how many answered
   * @param ratingCounts  how many gave rating 1, 2, … 5, in that order; empty below
   *                      {@link #MIN_PEOPLE} answers
   * @param meanRating    the mean rating; {@code null} below {@link #MIN_PEOPLE} ratings
   * @param standardError the standard error of that mean
   * @param comments      the comments, sorted by text so that their order says nothing; empty below
   *                      {@link #MIN_PEOPLE} answers
   */
  public record FeedbackSummary(FeedbackTrigger trigger, int answers, List<Integer> ratingCounts,
                                Double meanRating, Double standardError, List<String> comments) {

    public FeedbackSummary {
      ratingCounts = List.copyOf(ratingCounts);
      comments = List.copyOf(comments);
    }
  }
}
