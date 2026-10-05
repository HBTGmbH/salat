package de.hbt.salat.order.domain;

import de.hbt.salat.employee.domain.Employee;

/**
 * A person responsible for a customer order ({@link Customerorder#getResponsibleHbt()}) as plain
 * values (#1340): what another module needs to notify them, without the employee entity leaving
 * this module (→ ADR-0021).
 *
 * @param employeeId  the employee
 * @param name        {@link Employee#getName()}
 * @param salatUserId the login of the employee, {@code null} for an employee without one
 */
public record CustomerorderResponsible(long employeeId, String name, Long salatUserId) {

  public static CustomerorderResponsible of(Employee employee) {
    var salatUser = employee.getSalatUser();
    return new CustomerorderResponsible(employee.getId(), employee.getName(),
        salatUser == null ? null : salatUser.getId());
  }
}
