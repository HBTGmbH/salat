package de.hbt.salat.dailyreport.persistence;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.employee.domain.Employeecontract;

/**
 * References to the contract of a working day whose id arrives from a caller (#1532), the pattern of
 * {@code order.persistence.EmployeecontractReferences}: a reference rather than the loaded entity, taken here rather
 * than from a service of the employee module, which would hand an entity across the module boundary (ADR-0021,
 * {@code ArchitectureTest.noEntityCrossesAModuleBoundaryThroughAService}). Whether the person may write the day is
 * checked by the service that saves it.
 */
@Component
@RequiredArgsConstructor
public class ContractReferences {

  private final EntityManager entityManager;

  public Employeecontract employeecontract(long employeecontractId) {
    return entityManager.getReference(Employeecontract.class, employeecontractId);
  }
}
