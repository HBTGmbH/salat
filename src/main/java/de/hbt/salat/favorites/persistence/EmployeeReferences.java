package de.hbt.salat.favorites.persistence;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.employee.domain.Employee;

/**
 * References to the person a group of favourites belongs to (#1414) — like
 * {@link EmployeeorderReferences}, a reference rather than an entity handed over by the employee
 * module (ADR-0036). The id is that of the logged-in person, so there is nothing left to check.
 */
@Component
@RequiredArgsConstructor
public class EmployeeReferences {

  private final EntityManager entityManager;

  public Employee employee(long employeeId) {
    return entityManager.getReference(Employee.class, employeeId);
  }
}
