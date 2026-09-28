package de.hbt.salat.customer.event;

import de.hbt.salat.common.event.DomainObjectDeleteEvent;

public class CustomerDeleteEvent extends DomainObjectDeleteEvent {

  public CustomerDeleteEvent(long id) {
    super(id);
  }

}
