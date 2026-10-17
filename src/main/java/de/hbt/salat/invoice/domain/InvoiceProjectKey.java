package de.hbt.salat.invoice.domain;

/**
 * A project key of the ticket selection (#1390): picking it picks every ticket of that project.
 *
 * @param key         the part of a ticket key before the hyphen, {@code ABC}
 * @param ticketCount how many entries of the selection carry it
 */
public record InvoiceProjectKey(String key, int ticketCount) {}
