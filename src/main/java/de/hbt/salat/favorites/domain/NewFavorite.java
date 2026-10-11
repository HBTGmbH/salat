package de.hbt.salat.favorites.domain;

import java.time.Duration;
import java.util.List;

/**
 * What a favourite is made of when it is added (#1369): the employee order by id, as the booking
 * form and the REST client send it, and the booking it stands for. There is no id — a favourite is
 * always added, never written over another by an id in the request — and no person: that is the
 * person of the employee order.
 *
 * @param groupId the group it is sorted into (#1414), {@code null} for none. Either way it stands at
 *                the top of its section, where it is found again right away.
 */
public record NewFavorite(long employeeorderId, Integer hours, Integer minutes, String comment,
                          List<String> ticketReferences, Long groupId) {

  public NewFavorite {
    ticketReferences = ticketReferences == null ? List.of() : List.copyOf(ticketReferences);
  }

  /**
   * A favourite for a booking of this duration (#1400). The favourite keeps hours and minutes apart,
   * as it stores them; a caller hands over the duration as one value.
   */
  public static NewFavorite of(long employeeorderId, Duration duration, String comment,
                               List<String> ticketReferences, Long groupId) {
    return new NewFavorite(employeeorderId, Math.toIntExact(duration.toHours()), duration.toMinutesPart(), comment,
        ticketReferences, groupId);
  }

  /** A favourite without a group. */
  public NewFavorite(long employeeorderId, Integer hours, Integer minutes, String comment,
                     List<String> ticketReferences) {
    this(employeeorderId, hours, minutes, comment, ticketReferences, null);
  }
}
