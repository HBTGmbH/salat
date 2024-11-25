package org.tb.common.event;

import java.util.Set;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import org.tb.common.ServiceFeedbackMessage;

@Getter
public abstract class VetoableEvent extends ApplicationEvent {

  private boolean vetoed;
  private Set<ServiceFeedbackMessage> messages;

  public VetoableEvent(Object source) {
    super(source);
    messages = Set.of();
  }

  public void vetoed(ServiceFeedbackMessage... messages) {
    this.messages = Set.of(messages);
    this.vetoed = true;
  }

}
