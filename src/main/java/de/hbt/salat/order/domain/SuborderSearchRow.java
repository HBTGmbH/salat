package de.hbt.salat.order.domain;

import java.time.LocalDate;

/**
 * A suborder as the command palette finds it (#1157): plain values of the suborder, its order and
 * the order's customer, read in one query. {@code parentId} is the parent suborder, from which the
 * complete sign is built for the few rows that are shown.
 */
public record SuborderSearchRow(long id, String sign, String shortdescription, Long parentId,
    long customerorderId, String customerorderSign, String customerorderShortdescription,
    String customerShortname, Boolean hide, Boolean customerorderHide, LocalDate untilDate) {
}
