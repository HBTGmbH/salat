package org.tb.common.viewhelper.fragment;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class TextareaParams {
    private final String field;
    @Builder.Default
    private final boolean required = false;
    @Builder.Default
    private final int rows = 3;
    @Builder.Default
    private final boolean monospace = false;
    private final String helpText;
}
