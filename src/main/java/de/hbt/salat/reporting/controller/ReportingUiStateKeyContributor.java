package de.hbt.salat.reporting.controller;

import java.util.Map;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.web.UiStateKey;
import de.hbt.salat.common.web.UiStateKeyContributor;

@Component
public class ReportingUiStateKeyContributor implements UiStateKeyContributor {

    public static final UiStateKey REPORT_FILTER = new UiStateKey("report.Filter");

    @Override
    public Map<String, UiStateKey> getParamToKeyMappings() {
        return Map.of("fReportFilter", REPORT_FILTER);
    }
}
