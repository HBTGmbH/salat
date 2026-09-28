package de.hbt.salat.order.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(description = "Repräsentiert eine Unterauftragsinformation zu einem Mitarbeiterauftrag")
public class SuborderData {

    @Schema(description = "Eindeutige ID des Unterauftrags", example = "123")
    private final long id;

    @Schema(description = "Beschreibung des Unterauftrags, vollqualifiziert inkl. Beschreibungen der übergeordneten Aufträge", example = "URLAUB Urlaub / 2025 Urlaub 2025")
    private final String label;

    @Schema(description = "Gibt an, ob ein Kommentar für diesen Unterauftrag erforderlich ist")
    private final boolean commentRequired;

}
