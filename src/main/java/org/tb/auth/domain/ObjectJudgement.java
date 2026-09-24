package org.tb.auth.domain;

/**
 * What a module makes of a typed object id (#1074).
 *
 * <p>The two failing outcomes are deliberately not the same. A wrong <em>format</em> can never become right, so it is
 * refused at the field. An <em>unknown</em> value may well be right tomorrow — a rule is allowed to precede the order
 * or the ETL definition it is about, and an order may expire while its rule stays — so it is saved and pointed out.
 */
public enum ObjectJudgement {

  /** Fits the format and is known. */
  VALID,

  /** Fits the format, but the module does not (yet) know an object of that id. Saved, with a hint. */
  UNKNOWN,

  /** Cannot be an object of this category, whatever the data says. Refused at the field. */
  MALFORMED

}
