package de.hbt.salat.order.event;

import de.hbt.salat.common.event.DomainObjectUpdateEvent;
import de.hbt.salat.order.domain.Customerorder;

public class CustomerorderUpdateEvent extends DomainObjectUpdateEvent<Customerorder> {

  public CustomerorderUpdateEvent(Customerorder source) {
    super(source);
  }

}
