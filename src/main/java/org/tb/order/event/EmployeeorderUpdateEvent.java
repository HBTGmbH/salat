package org.tb.order.event;

import org.tb.common.event.DomainObjectUpdateEvent;
import org.tb.order.domain.Employeeorder;

public class EmployeeorderUpdateEvent extends DomainObjectUpdateEvent<Employeeorder> {

  public EmployeeorderUpdateEvent(Employeeorder source) {
    super(source);
  }

}
