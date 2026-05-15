package org.tb.employee.event;

import lombok.Getter;

@Getter
public class EmployeeAnonymizedEvent {

  private final long employeeId;

  public EmployeeAnonymizedEvent(long employeeId) {
    this.employeeId = employeeId;
  }

}
