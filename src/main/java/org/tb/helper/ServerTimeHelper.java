package org.tb.helper;

import static org.tb.util.DateUtils.format;

import org.springframework.stereotype.Component;
import org.tb.util.DateUtils;

@Component
public class ServerTimeHelper {

  public String getServerTime() {
    return format(DateUtils.now(), "dd.MM.yyyy HH:mm:ss");
  }

}
