package org.tb.common.event;

import java.util.List;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import org.tb.common.ServiceFeedbackMessage;

@Getter
public abstract class VetoableEvent extends ApplicationEvent {

  private boolean vetoed;
  private List<ServiceFeedbackMessage> messages;

  public VetoableEvent(Object source) {
    super(source);
    messages = List.of();
  }

  public void vetoed(ServiceFeedbackMessage... messages) {
    this.messages = List.of(messages);
    this.vetoed = true;
  }

  public void vetoed(List<ServiceFeedbackMessage> messages) {
    this.messages = messages;
    this.vetoed = true;
  }

}
