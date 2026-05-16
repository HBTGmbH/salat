package org.tb.common.thymeleaf;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class DangerZoneModalParams {
    private final String modalId;
    private final String modalTitle;
    private final String warningMessage;
    private final String submitLabel;
}
