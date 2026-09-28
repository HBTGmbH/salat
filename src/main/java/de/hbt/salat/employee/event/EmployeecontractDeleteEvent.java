package de.hbt.salat.employee.event;

import de.hbt.salat.common.event.DomainObjectDeleteEvent;

public class EmployeecontractDeleteEvent extends DomainObjectDeleteEvent {

  public EmployeecontractDeleteEvent(long id) {
    super(id);
  }

}
