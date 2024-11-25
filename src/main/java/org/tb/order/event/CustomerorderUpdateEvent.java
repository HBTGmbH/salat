package org.tb.order.event;

import org.tb.common.event.DomainObjectUpdateEvent;
import org.tb.order.domain.Customerorder;

public class CustomerorderUpdateEvent extends DomainObjectUpdateEvent<Customerorder> {

  public CustomerorderUpdateEvent(Customerorder source) {
    super(source);
  }

}
