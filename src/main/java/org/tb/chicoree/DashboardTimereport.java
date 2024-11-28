package org.tb.chicoree;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.domain.TimereportDTO;

@Data
@RequiredArgsConstructor
public class DashboardTimereport {

  private final String title;
  private final String duration;
  private final String comment;
  private final String id;

  public static DashboardTimereport valueOf(TimereportDTO timereport) {
    var title = timereport.getCustomerorderSign()
        + "/" + timereport.getCustomerorderDescription()
        + " - " + timereport.getCompleteOrderSign()
        + "/" + timereport.getSuborderDescription();
    var duration = DurationUtils.format(timereport.getDuration());
    return new DashboardTimereport(title, duration, timereport.getTaskdescription(), String.valueOf(timereport.getId()));
  }

}
