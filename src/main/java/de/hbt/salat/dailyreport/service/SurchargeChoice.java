package de.hbt.salat.dailyreport.service;

import de.hbt.salat.order.domain.SurchargeKind;

/**
 * The time surcharge flag a new booking is created with (#1469).
 *
 * @param kind          the kind, {@code null} for none
 * @param storedPercent the rate a stored booking of the same kind on the same employee order was flagged
 *                      with, where the new booking replaces it — an import or {@code PUT /list} deletes and
 *                      creates again what an edit would change, and keeps the rate as an edit does;
 *                      {@code null} takes the rate the suborder offers now
 * @param dayPreset     instead of a kind: the kind of the day, holiday before Saturday or Sunday, where the
 *                      suborder offers it — what the booking form preselects, for the one-click bookings
 *                      that have no form
 */
public record SurchargeChoice(SurchargeKind kind, Integer storedPercent, boolean dayPreset) {

  public static final SurchargeChoice NONE = new SurchargeChoice(null, null, false);

  public static final SurchargeChoice DAY_PRESET = new SurchargeChoice(null, null, true);

  public static SurchargeChoice of(SurchargeKind kind) {
    return new SurchargeChoice(kind, null, false);
  }

  /** {@code kind} at {@code storedPercent}, the rate of the stored booking replaced; see the parameter. */
  public static SurchargeChoice keeping(SurchargeKind kind, Integer storedPercent) {
    return new SurchargeChoice(kind, kind != null ? storedPercent : null, false);
  }
}
