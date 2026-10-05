package de.hbt.salat.order.domain;

import java.time.LocalDate;

/**
 * A suborder as the command palette finds it (#1157): plain values of the suborder, its order and
 * the order's customer, read in one query, the complete sign stored with the suborder (#1342) among them.
 */
public record SuborderSearchRow(long id, String sign, String shortdescription, String completeOrderSign,
    long customerorderId, String customerorderSign, String customerorderShortdescription,
    String customerShortname, Boolean hide, Boolean customerorderHide, LocalDate untilDate) {
}
