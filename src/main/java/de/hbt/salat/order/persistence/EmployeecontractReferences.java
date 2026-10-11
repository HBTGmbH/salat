package de.hbt.salat.order.persistence;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.employee.domain.Employeecontract;

/**
 * References to the contract of an employee order whose id arrives from another module (#1459).
 *
 * <p>A reference rather than the loaded entity, and taken here rather than from a service of the
 * employee module: such a service would hand an entity across the module boundary (ADR-0021,
 * {@code ArchitectureTest.noEntityCrossesAModuleBoundaryThroughAService}). The reference only
 * carries the id into the foreign key; whether the contract exists the caller knows from the
 * bookings it starts from.
 */
@Component
@RequiredArgsConstructor
public class EmployeecontractReferences {

  private final EntityManager entityManager;

  public Employeecontract employeecontract(long employeecontractId) {
    return entityManager.getReference(Employeecontract.class, employeecontractId);
  }
}
