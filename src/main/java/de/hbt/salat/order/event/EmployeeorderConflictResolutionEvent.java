package de.hbt.salat.order.event;

import lombok.Getter;
import de.hbt.salat.common.event.VetoableEvent;
import de.hbt.salat.order.domain.Employeeorder;

public class EmployeeorderConflictResolutionEvent extends VetoableEvent {

  @Getter
  private final Employeeorder updatingEmployeeorder;
  @Getter
  private final Employeeorder conflictingEmployeeorder;

  public EmployeeorderConflictResolutionEvent(Employeeorder updatingEmployeeorder, Employeeorder conflictingEmployeeorder) {
    this.updatingEmployeeorder = updatingEmployeeorder;
    this.conflictingEmployeeorder = conflictingEmployeeorder;
  }

}
