package de.hbt.salat.budget.persistence;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;

/**
 * References to order, suborder and person for plans, rates, flat rates and cost assignments, whose
 * scope arrives as ids (#1367).
 *
 * <p>A reference rather than the loaded entity, and taken here rather than from a service of another
 * module: such a service would hand an entity across the module boundary (ADR-0021,
 * {@code ArchitectureTest.noEntityCrossesAModuleBoundaryThroughAService}). The reference only
 * carries the id into the foreign key, it loads nothing — whether the record exists the caller has
 * already asked the owning module. {@code null} for {@code null}: suborder and person are optional
 * on several of them.
 */
@Component
@RequiredArgsConstructor
public class MasterDataReferences {

  private final EntityManager entityManager;

  public Customerorder customerorder(Long customerorderId) {
    return customerorderId == null ? null : entityManager.getReference(Customerorder.class, customerorderId);
  }

  public Suborder suborder(Long suborderId) {
    return suborderId == null ? null : entityManager.getReference(Suborder.class, suborderId);
  }

  public Employee employee(Long employeeId) {
    return employeeId == null ? null : entityManager.getReference(Employee.class, employeeId);
  }
}
