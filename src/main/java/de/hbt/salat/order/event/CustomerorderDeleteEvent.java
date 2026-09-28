package de.hbt.salat.order.event;

import de.hbt.salat.common.event.DomainObjectDeleteEvent;

public class CustomerorderDeleteEvent extends DomainObjectDeleteEvent {

  public CustomerorderDeleteEvent(long id) {
    super(id);
  }

}
