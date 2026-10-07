package de.hbt.salat.favorites.rest;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Jacksonized
@Schema(description = "Favorite für eine Zeitbuchung", name = "Favorite")
public class FavoriteDTO implements Serializable {
  @Schema(description = "Eindeutige ID des Favoriten, wird vom System vergeben", example = "1")
  private Long id;

  @Schema(description = "ID des zugehörigen Mitarbeiterauftrags", example = "42", requiredMode = REQUIRED)
  private Long employeeorderId;

  @Schema(description = "Anzahl der Stunden für die Zeitbuchung", example = "8", minimum = "0", maximum = "24", requiredMode = REQUIRED)
  private int hours;

  @Schema(description = "Anzahl der Minuten für die Zeitbuchung", example = "30", minimum = "0", maximum = "59", requiredMode = REQUIRED)
  private int minutes;

  @Schema(description = "Kommentar zur Zeitbuchung", example = "API-4511 Entwicklung neuer Features", maxLength = 255)
  private String comment;

  /**
   * The first reference, kept for clients from before #1326. Read: the first of {@link #ticketReferences},
   * {@code null} without one. Written: the only reference, where {@link #ticketReferences} is missing.
   */
  @Schema(description = "Erste Ticket-Referenz, für Clients, die nur eine kennen. Beim Lesen die erste aus "
      + "ticketReferences oder null; beim Schreiben die einzige Referenz, wenn ticketReferences fehlt.",
      example = "PROJ-123", maxLength = 64, nullable = true)
  private String ticketReference;

  @Schema(description = "Ticket-Referenzen in ihrer Reihenfolge, je höchstens 64 Zeichen. Ein Ticket-Schlüssel "
      + "wird in Großbuchstaben gespeichert. Wie viele erlaubt sind, legt der Unterauftrag fest; geprüft wird beim "
      + "Anwenden des Favoriten.", example = "[\"PROJ-123\", \"PROJ-130\"]", nullable = true)
  private List<String> ticketReferences;

  @Schema(description = "Name der Gruppe, in die die Person den Favoriten einsortiert hat; null ohne Gruppe. "
      + "Nur lesend: Gruppen werden in der Anwendung angelegt und geordnet, ein neuer Favorit steht ohne Gruppe.",
      example = "Wartung", nullable = true, accessMode = Schema.AccessMode.READ_ONLY)
  private String groupName;
}
