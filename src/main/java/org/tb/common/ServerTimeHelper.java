package org.tb.common;

import static org.tb.common.GlobalConstants.DEFAULT_TIMEZONE_ID;
import static org.tb.common.util.DateUtils.formatDateTime;

import org.springframework.stereotype.Component;
import org.tb.common.util.ClockProvider;
import org.tb.common.util.DateTimeUtils;

@Component
public class ServerTimeHelper {

  public String getServerTime() {
    return formatDateTime(DateTimeUtils.now(), "dd.MM.yyyy HH:mm:ss");
  }

  /**
   * The server's current instant. Together with {@link #getTimeZone()} it lets the command palette
   * count its day jumps from the server's today (#1155): "gestern" has to name the same day the
   * daily view shows for it. The instant rather than the date, because the browser carries it
   * forward with its own clock — a date would stand still in a tab left open past midnight.
   */
  public long getEpochMillis() {
    return ClockProvider.instant().toEpochMilli();
  }

  /** The zone in which the server's today begins and ends. */
  public String getTimeZone() {
    return DEFAULT_TIMEZONE_ID;
  }

}
