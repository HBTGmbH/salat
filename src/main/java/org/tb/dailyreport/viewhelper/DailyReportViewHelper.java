package org.tb.dailyreport.viewhelper;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.tb.common.OptionItem;

@Data
@AllArgsConstructor
public class DailyReportViewHelper implements Serializable {

  private final boolean createTimereports;
  private final boolean editTimereports;
  private final boolean displayWorkingDay;
  private final boolean displayEmployeeInfo;
  private final boolean useFavorites;

  public boolean containsMinuteOption(List<OptionItem> items, int minutes) {
    return items
        .stream()
        .map(OptionItem::getValue)
        .anyMatch(value -> value.equals(String.valueOf(minutes)));
  }

}
