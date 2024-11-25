package org.tb.employee.event;

import org.tb.common.event.DomainObjectDeleteEvent;

public class EmployeecontractDeleteEvent extends DomainObjectDeleteEvent {

  public EmployeecontractDeleteEvent(long id) {
    super(id);
  }

}
