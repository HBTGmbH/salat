package org.tb.order.event;

import org.tb.common.event.DomainObjectDeleteEvent;
import org.tb.common.event.DomainObjectUpdateEvent;

public class EmployeeorderDeleteEvent extends DomainObjectDeleteEvent {

  public EmployeeorderDeleteEvent(long id) {
    super(id);
  }

}
