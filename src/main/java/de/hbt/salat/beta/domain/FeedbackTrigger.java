package de.hbt.salat.beta.domain;

/** What led to a feedback (#1447). */
public enum FeedbackTrigger {

  /** The person used the beta often enough to be asked how helpful it is. */
  USE,
  /** The person switched the beta off and said why. */
  SWITCH_OFF
}
