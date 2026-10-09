package de.hbt.salat.beta.domain;

/** Where a person stands with the question how helpful a beta is (#1447). */
public enum FeedbackState {

  /** Not asked yet. */
  OPEN,
  /** Asked, and "later" chosen: asked again from {@code feedbackAskAfter} on. */
  POSTPONED,
  /** Answered; not asked again. */
  ANSWERED,
  /** "Do not ask again" chosen. */
  DECLINED
}
