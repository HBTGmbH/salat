package de.hbt.salat.order.event;

import de.hbt.salat.common.event.DomainObjectDeleteEvent;

public class EmployeeorderDeleteEvent extends DomainObjectDeleteEvent {

  public EmployeeorderDeleteEvent(long id) {
    super(id);
  }

}
