package org.tb.dailyreport.viewhelper;

import java.io.Serializable;
import java.util.List;
import org.tb.common.OptionItem;

public class DailyReportViewHelper implements Serializable {

  public boolean containsMinuteOption(List<OptionItem> items, int minutes) {
    return items
        .stream()
        .map(OptionItem::getValue)
        .filter(value -> value.equals(String.valueOf(minutes)))
        .findAny()
        .isPresent();
  }

}
