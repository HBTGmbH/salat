package org.tb.favorites.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
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

  @Schema(description = "ID des zugehörigen Mitarbeiterauftrags", example = "42", required = true)
  private Long employeeorderId;

  @Schema(description = "Anzahl der Stunden für die Zeitbuchung", example = "8", minimum = "0", maximum = "24")
  private int hours;

  @Schema(description = "Anzahl der Minuten für die Zeitbuchung", example = "30", minimum = "0", maximum = "59")
  private int minutes;

  @Schema(description = "Kommentar zur Zeitbuchung", example = "API-4511 Entwicklung neuer Features", maxLength = 255)
  private String comment;
}
