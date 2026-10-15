package de.hbt.salat.dailyreport.controller;

import de.hbt.salat.order.domain.SurchargeKind;

/**
 * One kind of time surcharge as the booking form offers it (#1469).
 *
 * @param percent    the rate a booking on the selected suborder is flagged with, {@code null} where it offers none
 * @param selectable whether the booking may be flagged with it: the suborder offers it, and a kind bound to a
 *                   day falls on the day of the booking
 */
public record SurchargeOption(SurchargeKind kind, Integer percent, boolean selectable) {}
