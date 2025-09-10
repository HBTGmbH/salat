package org.tb.order.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@ToString
@Builder(toBuilder = true)
@RequiredArgsConstructor
@AllArgsConstructor
@Schema(description = "Repräsentiert einen Auftrag (oberste Ebene) oder Unterauftrag in der hierarchischen Struktur")
public class OrderData {

  @Schema(description = "Eindeutige ID des Auftrags / Unterauftrags", example = "42")
  private final Long id;

  @Schema(description = "Kurzbeschreibung des Auftrags / Unterauftrags", example = "Softwareentwicklung")
  private final String label;

  @Schema(description = "Gibt an, ob ein Kommentar für diesen Auftrag / Unterauftrag erforderlich ist")
  private final boolean commentRequired;

  @Setter
  @Schema(description = "Liste der Unteraufträge, falls vorhanden")
  private Collection<OrderData> suborder;

  @Schema(description = "ID des zugehörigen Mitarbeiterauftrags, falls vorhanden, ansonsten 0", example = "123")
  private final long employeeorderId;

}
