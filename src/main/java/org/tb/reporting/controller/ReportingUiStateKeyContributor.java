package org.tb.reporting.controller;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.tb.common.web.UiStateKey;
import org.tb.common.web.UiStateKeyContributor;

@Component
public class ReportingUiStateKeyContributor implements UiStateKeyContributor {

    public static final UiStateKey REPORT_FILTER = new UiStateKey("report.Filter");

    @Override
    public Map<String, UiStateKey> getParamToKeyMappings() {
        return Map.of("rFilter", REPORT_FILTER);
    }
}
