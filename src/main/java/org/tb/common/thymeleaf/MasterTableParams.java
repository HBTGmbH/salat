package org.tb.common.thymeleaf;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class MasterTableParams {
    private final String addLabel;
    @Builder.Default
    private final String addIcon = "ti-plus";
}
