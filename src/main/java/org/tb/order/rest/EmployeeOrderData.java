package org.tb.order.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@Builder
@RequiredArgsConstructor
@Schema(description = "Repräsentiert einen Mitarbeiterauftrag mit zugehörigem Unterauftrag")
public class EmployeeOrderData {
    @Schema(description = "Informationen zum zugehörigen Unterauftrag")
    private final SuborderData suborder;

    @Schema(description = "Eindeutige ID des Mitarbeiterauftrags", example = "42")
    private final long employeeorderId;
}
