package de.hbt.salat.order.domain;

/** How many orders or suborders carry a tag (#847), as counted over the join table. */
public record TagUsage(long tagId, long count) {}
