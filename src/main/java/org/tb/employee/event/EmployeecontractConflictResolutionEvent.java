package org.tb.employee.event;

import lombok.Getter;
import org.tb.common.event.VetoableEvent;
import org.tb.employee.domain.Employeecontract;

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
