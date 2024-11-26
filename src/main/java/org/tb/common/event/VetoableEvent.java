package org.tb.common.event;

import java.util.ArrayList;
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
    if(!vetoed) {
      this.messages = messages;
      this.vetoed = true;
    } else {
      this.messages = new ArrayList<>(this.messages);
      this.messages.addAll(messages);
    }
    throw new VetoedException(this);
  }

}
