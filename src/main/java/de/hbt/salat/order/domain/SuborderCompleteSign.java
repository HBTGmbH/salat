package de.hbt.salat.order.domain;

/** The complete order sign of a suborder by its id ({@link Suborder#getCompleteOrderSign()}, #1342). */
public record SuborderCompleteSign(long id, String completeOrderSign) {
}
