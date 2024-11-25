package org.tb.order.event;

import org.tb.common.event.DomainObjectUpdateEvent;
import org.tb.order.domain.Suborder;

public class SuborderUpdateEvent extends DomainObjectUpdateEvent<Suborder> {

  public SuborderUpdateEvent(Suborder source) {
    super(source);
  }

}
