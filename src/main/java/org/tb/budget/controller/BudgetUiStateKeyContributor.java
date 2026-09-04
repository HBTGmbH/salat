package org.tb.budget.controller;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.tb.common.web.UiStateKey;
import org.tb.common.web.UiStateKeyContributor;

/**
 * UiState keys owned by the budget module (ADR-0016). The parameter names are prefixed because the
 * mapping is global across all contributors — a plain {@code segmentId} would collide with any
 * other module introducing one.
 */
@Component
public class BudgetUiStateKeyContributor implements UiStateKeyContributor {

    public static final UiStateKey DASHBOARD_SEGMENT_ID = new UiStateKey("budgetDashboard.SegmentId");
    public static final UiStateKey DASHBOARD_RESPONSIBLE_ID = new UiStateKey("budgetDashboard.ResponsibleId");

    private static final Map<String, UiStateKey> PARAM_TO_KEY;
    static {
        var map = new HashMap<String, UiStateKey>();
        map.put("budgetSegmentId", DASHBOARD_SEGMENT_ID);
        map.put("budgetResponsibleId", DASHBOARD_RESPONSIBLE_ID);
        PARAM_TO_KEY = Collections.unmodifiableMap(map);
    }

    @Override
    public Map<String, UiStateKey> getParamToKeyMappings() {
        return PARAM_TO_KEY;
    }
}
