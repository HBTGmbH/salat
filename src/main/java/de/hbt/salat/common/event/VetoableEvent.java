package de.hbt.salat.common.event;

import java.util.List;
import lombok.Getter;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;
import de.hbt.salat.common.exception.VetoedException;

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
