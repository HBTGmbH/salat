package org.tb.common.event;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

public class LoggingEvent {

  @Getter
  private List<String> eventLog = new ArrayList<>();

  public void addLog(String log) {
    eventLog.add(log);
  }

}
