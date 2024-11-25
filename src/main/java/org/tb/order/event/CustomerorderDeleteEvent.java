package org.tb.order.event;

import org.tb.common.event.DomainObjectDeleteEvent;

public class CustomerorderDeleteEvent extends DomainObjectDeleteEvent {

  public CustomerorderDeleteEvent(long id) {
    super(id);
  }

}
