package org.tb.common;

import static org.tb.common.util.DateUtils.formatDateTime;

import org.springframework.stereotype.Component;
import org.tb.common.util.DateUtils;

@Component
public class ServerTimeHelper {

  public String getServerTime() {
    return formatDateTime(DateUtils.now(), "dd.MM.yyyy HH:mm:ss");
  }

}
