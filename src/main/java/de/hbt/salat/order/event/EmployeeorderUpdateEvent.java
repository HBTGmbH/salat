package de.hbt.salat.order.event;

import de.hbt.salat.common.event.DomainObjectUpdateEvent;
import de.hbt.salat.order.domain.Employeeorder;

public class EmployeeorderUpdateEvent extends DomainObjectUpdateEvent<Employeeorder> {

  public EmployeeorderUpdateEvent(Employeeorder source) {
    super(source);
  }

}
