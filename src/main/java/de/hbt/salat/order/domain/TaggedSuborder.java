package de.hbt.salat.order.domain;

/** A suborder carrying one of the sought tags, and the order whose tree it stands in (#847). */
public record TaggedSuborder(long id, long customerorderId) {
}
