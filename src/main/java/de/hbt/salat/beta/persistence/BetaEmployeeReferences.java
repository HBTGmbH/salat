package de.hbt.salat.beta.persistence;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.employee.domain.Employee;

/**
 * References to the person a use or a participation in a beta belongs to (#1447) — a reference
 * rather than an entity handed over by the employee module (ADR-0036). The id is that of the
 * logged-in person, so there is nothing left to check.
 */
@Component
@RequiredArgsConstructor
public class BetaEmployeeReferences {

  private final EntityManager entityManager;

  public Employee employee(long employeeId) {
    return entityManager.getReference(Employee.class, employeeId);
  }
}
