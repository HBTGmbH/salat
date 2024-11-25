package org.tb.order.event;

import org.tb.common.event.DomainObjectDeleteEvent;

public class SuborderDeleteEvent extends DomainObjectDeleteEvent {

  public SuborderDeleteEvent(long id) {
    super(id);
  }

}
