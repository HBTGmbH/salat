package de.hbt.salat.customer.domain;

/**
 * A customer as values, with what the customer list shows everybody who may open it (#1534) — for a module that
 * describes a customer without handling the entity (ADR-0021).
 *
 * @param shortName the short name, or the shortened name where there is none ({@link Customer#getShortname()})
 * @param intermediary whether the customer may act as intermediary of an order (#1536)
 * @param segment   the customer segment, {@code null} without one
 */
public record CustomerReadModel(long id, String shortName, String name, String address, boolean hidden,
    boolean intermediary, Segment segment) {

  public static CustomerReadModel of(Customer customer) {
    var segment = customer.getSegment();
    return new CustomerReadModel(customer.getId(), customer.getShortname(), customer.getName(), customer.getAddress(),
        customer.getHide(), customer.isIntermediary(), segment == null ? null : new Segment(segment.getId(), segment.getName()));
  }

  public record Segment(long id, String name) {
  }
}
