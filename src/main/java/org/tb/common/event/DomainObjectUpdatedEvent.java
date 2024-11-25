package org.tb.common.event;

import lombok.Getter;
import org.springframework.data.domain.Persistable;

@Getter
public class DomainObjectUpdatedEvent<T extends Persistable<Long>> extends VetoableEvent {

  private final T domainObject;

  public DomainObjectUpdatedEvent(T source) {
    super(source);
    this.domainObject = source;
  }

}
