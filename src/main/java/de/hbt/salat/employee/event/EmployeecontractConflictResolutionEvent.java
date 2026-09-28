package de.hbt.salat.employee.event;

import lombok.Getter;
import de.hbt.salat.common.event.VetoableEvent;
import de.hbt.salat.employee.domain.Employeecontract;

public class EmployeecontractConflictResolutionEvent extends VetoableEvent {

  @Getter
  private final Employeecontract updatingEmployeecontract;
  @Getter
  private final Employeecontract conflictingEmployeecontract;

  public EmployeecontractConflictResolutionEvent(Employeecontract updatingEmployeecontract, Employeecontract conflictingEmployeecontract) {
    this.updatingEmployeecontract = updatingEmployeecontract;
    this.conflictingEmployeecontract = conflictingEmployeecontract;
  }

}
