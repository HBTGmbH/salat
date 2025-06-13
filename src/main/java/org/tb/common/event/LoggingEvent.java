package org.tb.common.event;

import java.util.List;
import lombok.Getter;

public class LoggingEvent {

  @Getter
  private List<String> eventLog;

  public void addLog(String log) {
    eventLog.add(log);
  }

}
