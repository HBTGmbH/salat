package org.tb.common.thymeleaf;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class DangerZoneParams {
    private final String cardTitle;
    private final String description;
    private final String buttonLabel;
    private final String modalId;
}
