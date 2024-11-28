package org.tb.employee.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class EmployeecontractChangedEvent extends ApplicationEvent {

  private final long employeecontractId;

  public EmployeecontractChangedEvent(Object source, long employeecontractId) {
    super(source);
    this.employeecontractId = employeecontractId;
  }

}
