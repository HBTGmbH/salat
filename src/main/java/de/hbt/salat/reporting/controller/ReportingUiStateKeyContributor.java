package de.hbt.salat.reporting.controller;

import java.util.Map;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.web.UiStateKey;
import de.hbt.salat.common.web.UiStateKeyContributor;

@Component
public class ReportingUiStateKeyContributor implements UiStateKeyContributor {

    public static final UiStateKey REPORT_FILTER = new UiStateKey("report.Filter");
    /** One group of the report list (#1523). */
    public static final UiStateKey REPORT_GROUP_FILTER = new UiStateKey("report.GroupFilter");

    @Override
    public Map<String, UiStateKey> getParamToKeyMappings() {
        return Map.of("fReportFilter", REPORT_FILTER, "fReportGroupId", REPORT_GROUP_FILTER);
    }
}
