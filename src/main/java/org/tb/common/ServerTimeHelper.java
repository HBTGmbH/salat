package org.tb.common;

import static org.tb.common.util.DateUtils.formatDateTime;

import org.springframework.stereotype.Component;
import org.tb.common.util.DateTimeUtils;
import org.tb.common.util.DateUtils;

@Component
public class ServerTimeHelper {

  public String getServerTime() {
    return formatDateTime(DateTimeUtils.now(), "dd.MM.yyyy HH:mm:ss");
  }

  /**
   * Today as the server sees it, in ISO form. The command palette resolves its day jumps against
   * this value rather than against the clock of the browser (#1155): "gestern" has to name the same
   * day the daily view shows for it, and that view counts from the server's today.
   */
  public String getToday() {
    return DateUtils.today().toString();
  }

}
