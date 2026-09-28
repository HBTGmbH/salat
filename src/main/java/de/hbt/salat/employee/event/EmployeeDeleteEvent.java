package de.hbt.salat.employee.event;

import de.hbt.salat.common.event.DomainObjectDeleteEvent;

public class EmployeeDeleteEvent extends DomainObjectDeleteEvent {

  public EmployeeDeleteEvent(long id) {
    super(id);
  }

}
