package de.hbt.salat.achievement.persistence;

import de.hbt.salat.employee.domain.Employee;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The person an achievement belongs to, as a reference rather than an entity handed over by the
 * employee module (ADR-0036). The ids come from the contributors, which read them from the employees.
 */
@Component
@RequiredArgsConstructor
public class AchievementEmployeeReferences {

  private final EntityManager entityManager;

  public Employee employee(long employeeId) {
    return entityManager.getReference(Employee.class, employeeId);
  }
}
