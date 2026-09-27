package org.tb.order.domain;

import java.time.LocalDate;

/**
 * A customer order as the command palette finds it (#1157): plain values, read in one query with
 * its customer, so that no association of the entity is loaded per row.
 */
public record CustomerorderSearchRow(long id, String sign, String shortdescription, String description,
    long customerId, String customerShortname, String customerName, Boolean hide, LocalDate untilDate) {
}
