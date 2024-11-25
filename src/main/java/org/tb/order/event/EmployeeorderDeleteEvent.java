package org.tb.order.event;

import org.tb.common.event.DomainObjectDeleteEvent;

public class EmployeeorderDeleteEvent extends DomainObjectDeleteEvent {

  public EmployeeorderDeleteEvent(long id) {
    super(id);
  }

}
