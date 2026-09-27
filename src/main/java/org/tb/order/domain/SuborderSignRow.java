package org.tb.order.domain;

/** One step of the parent chain of a suborder, for building its complete sign in batches. */
public record SuborderSignRow(long id, String sign, Long parentId) {
}
