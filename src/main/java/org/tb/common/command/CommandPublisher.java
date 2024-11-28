package org.tb.common.command;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommandPublisher {

  private final ApplicationEventPublisher eventPublisher;

  public void publish(CommandEvent<?> event) {
    eventPublisher.publishEvent(event);
  }

}
