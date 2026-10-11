package de.hbt.salat.favorites.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A favourite as other modules and the views read it (#1414): plain values, no entity crosses the
 * module boundary (ADR-0021). {@code suborderLabel} is the suborder the favourite books on, as
 * {@code Auftrag/Unterauftrag - Kurzbeschreibung}.
 */
public record FavoriteEntry(long id, long employeeorderId, String suborderLabel, Duration duration,
                            String comment, List<String> ticketReferences, Long groupId, String groupName,
                            LocalDateTime lastUsed) {

  public FavoriteEntry {
    ticketReferences = ticketReferences == null ? List.of() : List.copyOf(ticketReferences);
  }
}
