package org.tb.dailyreport.event;

import java.util.List;
import lombok.Data;

@Data
public class TimereportsCreatedOrUpdatedEvent {

  private final List<Long> ids;

}
