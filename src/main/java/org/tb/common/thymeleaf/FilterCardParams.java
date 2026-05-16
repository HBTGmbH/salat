package org.tb.common.thymeleaf;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class FilterCardParams {
    private final String formKey;
    private final String hxTarget;
    @Builder.Default
    private final boolean showAdvancedToggle = true;
}
