package de.hbt.salat.order.domain;

/**
 * A person responsible for at least one customer order, as a filter offers it — plain values, so the
 * employee entity stays in its module (ADR-0021). The repository builds it in the query; it does not
 * hand out entities of another module.
 *
 * @param name {@code Employee#getName()}, concatenated in the query the same way
 */
public record ResponsibleOption(long id, String sign, String name, Boolean hide) {
}
