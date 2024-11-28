package org.tb.order.command;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.tb.common.command.CommandEvent;

@Builder
@Data
@AllArgsConstructor
public class GetTimereportMinutesCommandEvent implements CommandEvent<Map<Long, Duration>> {

  public enum OrderType { CUSTOMER, SUB, EMPLOYEE }

  private final List<Long> orderIds;
  private final OrderType orderType;
  private Map<Long, Duration> result;

  @Override
  public Map<Long, Duration> getResult() {
    return result;
  }

  @Override
  public void setResult(Map<Long, Duration> result) {
    this.result = result;
  }

}
