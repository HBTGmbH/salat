package de.hbt.salat.budget.domain;

/**
 * One person who can be picked in a bulk assignment (#953). Only people who actually booked in the
 * selected order, suborder and period are offered, so no combination can be chosen that is
 * necessarily empty.
 */
public record BulkAssignmentEmployee(long id, String sign, String name) {

}
