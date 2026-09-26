package org.tb.dailyreport.event;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class TimereportsCreatedOrUpdatedEvent {

  private final List<Long> ids;

  /**
   * The day a booking was on before this change, keyed by its id — only for bookings whose day
   * changed. A listener that reads only the new day misses the one the booking left (#1125).
   */
  private final Map<Long, LocalDate> previousReferencedays;

  /**
   * The contract a booking belonged to before this change, keyed by its id — only for bookings whose
   * contract changed. A listener that reads the contract from the booking sees only the new one
   * (#1128).
   */
  private final Map<Long, Long> previousEmployeecontractIds;

  public TimereportsCreatedOrUpdatedEvent(List<Long> ids) {
    this(ids, Map.of());
  }

  public TimereportsCreatedOrUpdatedEvent(List<Long> ids, Map<Long, LocalDate> previousReferencedays) {
    this(ids, previousReferencedays, Map.of());
  }

}
