package org.tb.order.event;

import lombok.Getter;
import org.tb.common.event.VetoableEvent;
import org.tb.order.domain.Employeeorder;

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
