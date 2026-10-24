package de.hbt.salat.employee.domain;

/**
 * The name of the person signed in under a login (#1523), for a module that knows people by their login
 * only. Spelled as {@link Employee#getName()}.
 */
public record EmployeeName(long salatUserId, String firstname, String lastname) {

  public String name() {
    return firstname + " " + lastname;
  }
}
