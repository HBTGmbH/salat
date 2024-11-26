package org.tb.employee.event;

import org.tb.common.event.DomainObjectDeleteEvent;

public class EmployeeDeleteEvent extends DomainObjectDeleteEvent {

  public EmployeeDeleteEvent(long id) {
    super(id);
  }

}
