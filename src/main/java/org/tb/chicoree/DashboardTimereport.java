package org.tb.chicoree;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.domain.Timereport;

@Data
@RequiredArgsConstructor
public class DashboardTimereport {

  private final String title;
  private final String duration;
  private final String comment;
  private final String id;

  public static DashboardTimereport valueOf(Timereport timereport) {
    var title = timereport.getSuborder().getCustomerorder().getSign()
        + "/" + timereport.getSuborder().getCustomerorder().getShortdescription()
        + " - " + timereport.getSuborder().getSign()
        + "/" + timereport.getSuborder().getShortdescription();
    var duration = DurationUtils.format(timereport.getDuration());
    return new DashboardTimereport(title, duration, timereport.getTaskdescription(), timereport.getId().toString());
  }

}
