package org.tb.common.thymeleaf;

import java.util.Collection;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class SelectInputParams {
    private final String field;
    @Builder.Default
    private final boolean required = false;
    private final Collection<?> options;
    private final String optionValue;
    private final String optionLabel;
    private final String helpText;
}
