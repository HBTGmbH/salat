package de.hbt.salat.order.event;

import de.hbt.salat.common.event.DomainObjectDeleteEvent;

public class SuborderDeleteEvent extends DomainObjectDeleteEvent {

  public SuborderDeleteEvent(long id) {
    super(id);
  }

}
