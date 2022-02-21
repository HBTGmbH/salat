package org.tb.helper;

import static java.util.TimeZone.getTimeZone;
import static org.tb.GlobalConstants.DEFAULT_TIMEZONE_ID;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import org.springframework.stereotype.Component;
import org.tb.util.DateUtils;

@Component
public class ServerTimeHelper {

  public String getServerTime() {
    DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    dateFormat.setTimeZone(getTimeZone(DEFAULT_TIMEZONE_ID));
    return dateFormat.format(DateUtils.now());
  }

}
