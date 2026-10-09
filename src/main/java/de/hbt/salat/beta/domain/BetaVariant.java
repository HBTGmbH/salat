package de.hbt.salat.beta.domain;

/**
 * Which side of a beta a counted use was made on (#1447). Uses are counted on both sides, so that a
 * beta is compared with the people who work without it in the same week, not with an earlier time.
 */
public enum BetaVariant {

  /** The person had the beta switched on. */
  BETA,
  /** The person had it switched off — the comparison group. */
  CLASSIC
}
