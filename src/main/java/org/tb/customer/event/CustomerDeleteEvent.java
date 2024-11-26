package org.tb.customer.event;

import org.tb.common.event.DomainObjectDeleteEvent;

public class CustomerDeleteEvent extends DomainObjectDeleteEvent {

  public CustomerDeleteEvent(long id) {
    super(id);
  }

}
