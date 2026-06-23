package org.tb.dailyreport.controller;

import org.springframework.stereotype.Component;
import org.tb.common.web.UiStateKey;
import org.tb.common.web.UiStateKeyContributor;

import java.util.Map;

import static java.util.Map.of;

@Component
public class DailyReportUiStateKeyContributor implements UiStateKeyContributor {

    //public static final UiStateKey SHOW_HIDDEN = new UiStateKey("showHidden");

    @Override
    public Map<String, UiStateKey> getParamToKeyMappings() {
        return of(
                // "filter", FILTER,

        );
    }
}
