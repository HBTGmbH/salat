package org.tb.common.event;

import lombok.Getter;

@Getter
public class DomainObjectDeleteEvent extends VetoableEvent {

  private final long id;

  public DomainObjectDeleteEvent(long id) {
    this.id = id;
  }

}
