package de.hbt.salat.customer.domain;

/**
 * A customer as a select of another module offers it (#1536): plain values, no entity (ADR-0021).
 *
 * @param shortname what {@link Customer#getShortname()} answers, including the one it makes up from the name
 */
public record CustomerOption(long id, String shortname, String name, boolean hide) {

  public static CustomerOption of(Customer customer) {
    return new CustomerOption(customer.getId(), customer.getShortname(), customer.getName(), customer.getHide());
  }
}
