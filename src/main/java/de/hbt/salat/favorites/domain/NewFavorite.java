package de.hbt.salat.favorites.domain;

import java.util.List;

/**
 * What a favourite is made of when it is added (#1369): the employee order by id, as the booking
 * form and the REST client send it, and the booking it stands for. There is no id — a favourite is
 * always added, never written over another by an id in the request — and no person: that is the
 * person of the employee order.
 */
public record NewFavorite(long employeeorderId, Integer hours, Integer minutes, String comment,
                          List<String> ticketReferences) {

  public NewFavorite {
    ticketReferences = ticketReferences == null ? List.of() : List.copyOf(ticketReferences);
  }
}
