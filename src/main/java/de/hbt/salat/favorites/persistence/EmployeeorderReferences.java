package de.hbt.salat.favorites.persistence;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.order.domain.Employeeorder;

/**
 * References to the employee order of a favourite, whose id arrives from the booking form or the
 * REST client (#1369).
 *
 * <p>A reference rather than the loaded entity, and taken here rather than from a service of the
 * order module: such a service would hand an entity across the module boundary (ADR-0021,
 * {@code ArchitectureTest.noEntityCrossesAModuleBoundaryThroughAService}). The reference only
 * carries the id into the foreign key, it loads nothing — whether the employee order exists and
 * whose it is the caller has already asked the order module.
 */
@Component
@RequiredArgsConstructor
public class EmployeeorderReferences {

  private final EntityManager entityManager;

  public Employeeorder employeeorder(long employeeorderId) {
    return entityManager.getReference(Employeeorder.class, employeeorderId);
  }
}
