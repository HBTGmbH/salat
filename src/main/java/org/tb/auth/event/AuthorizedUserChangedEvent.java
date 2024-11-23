package org.tb.auth.event;

import org.springframework.context.ApplicationEvent;

public class AuthorizedUserChangedEvent extends ApplicationEvent {

  public AuthorizedUserChangedEvent(Object source) {
    super(source);
  }

}
