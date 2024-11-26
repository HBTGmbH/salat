package org.tb.common.exception;

import lombok.Getter;
import org.tb.common.event.VetoableEvent;

@Getter
public class VetoedException extends ErrorCodeException {

  private final VetoableEvent event;

  public VetoedException(VetoableEvent event) {
    super(event.getMessages());
    this.event = event;
  }

}
