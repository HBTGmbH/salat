package org.tb.common.event;

import java.util.List;
import lombok.Getter;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.common.exception.VetoedException;

@Getter
public abstract class VetoableEvent extends LoggingEvent {

  private boolean vetoed;
  private List<ServiceFeedbackMessage> messages = List.of();

  public void veto(List<ServiceFeedbackMessage> messages) throws VetoedException {
    this.messages = messages;
    this.vetoed = true;
    throw new VetoedException(this);
  }

}
