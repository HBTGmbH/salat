package org.tb.common.event;

import lombok.Getter;
import org.springframework.data.domain.Persistable;

@Getter
public class DomainObjectUpdateEvent<T extends Persistable<Long>> extends VetoableEvent {

  private final T domainObject;

  public DomainObjectUpdateEvent(T domainObject) {
    this.domainObject = domainObject;
  }

}
