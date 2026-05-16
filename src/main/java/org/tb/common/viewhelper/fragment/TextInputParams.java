package org.tb.common.viewhelper.fragment;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class TextInputParams {
    private final String field;
    @Builder.Default
    private final boolean required = false;
    private final Integer maxlength;
    private final String helpText;
}
