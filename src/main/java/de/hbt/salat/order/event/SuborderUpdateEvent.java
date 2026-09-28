package de.hbt.salat.order.event;

import de.hbt.salat.common.event.DomainObjectUpdateEvent;
import de.hbt.salat.order.domain.Suborder;

public class SuborderUpdateEvent extends DomainObjectUpdateEvent<Suborder> {

  public SuborderUpdateEvent(Suborder source) {
    super(source);
  }

}
