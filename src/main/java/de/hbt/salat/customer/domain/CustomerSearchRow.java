package de.hbt.salat.customer.domain;

/** A customer as the command palette finds it (#1157): plain values, no entity. */
public record CustomerSearchRow(long id, String shortname, String name, Boolean hide) {
}
