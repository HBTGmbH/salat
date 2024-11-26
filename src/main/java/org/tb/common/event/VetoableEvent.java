package org.tb.common.event;

import java.util.List;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.common.exception.VetoedException;

@Getter
public abstract class VetoableEvent extends ApplicationEvent {

  private boolean vetoed;
  private List<ServiceFeedbackMessage> messages;

  public VetoableEvent(Object source) {
    super(source);
    messages = List.of();
  }

  public void veto(List<ServiceFeedbackMessage> messages) throws VetoedException {
    this.messages = messages;
    this.vetoed = true;
    throw new VetoedException(this);
  }

}
