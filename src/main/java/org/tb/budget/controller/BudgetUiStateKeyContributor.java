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

    /**
     * The customer order of the plan list, of the rate list and of the controlling filter —
     * deliberately one key for all three (#952, #1009). Whoever picks an order in one of them finds
     * it preselected in the others, the same way {@code orderId} and {@code customerOrderId} share a
     * key in the order module.
     */
    public static final UiStateKey CUSTOMER_ORDER_SIGN = new UiStateKey("budget.CustomerorderSign");

    /**
     * The two "show inactive" switches stay apart: in the plan list the switch means inactive budget
     * plans, in the rate list expired rates. Because the parameter mapping is global, telling them
     * apart requires two parameter names — hence the prefixes.
     */
    public static final UiStateKey BUDGET_SHOW_INACTIVE = new UiStateKey("budgetList.ShowInactive");
    public static final UiStateKey PRICING_SHOW_INACTIVE = new UiStateKey("pricingList.ShowInactive");

    /**
     * Whether the rate list also shows the rates of orders whose validity has expired (#957) — a
     * switch of its own, because it is about the order's validity and not the rate's.
     */
    public static final UiStateKey PRICING_SHOW_EXPIRED_ORDERS = new UiStateKey("pricingList.ShowExpiredOrders");

    private static final Map<String, UiStateKey> PARAM_TO_KEY;
    static {
        var map = new HashMap<String, UiStateKey>();
        map.put("fBudgetSegmentId", DASHBOARD_SEGMENT_ID);
        map.put("fBudgetResponsibleId", DASHBOARD_RESPONSIBLE_ID);
        map.put("fCustomerOrderSign", CUSTOMER_ORDER_SIGN);
        map.put("fBudgetShowInactive", BUDGET_SHOW_INACTIVE);
        map.put("fPricingShowInactive", PRICING_SHOW_INACTIVE);
        map.put("fPricingShowExpiredOrders", PRICING_SHOW_EXPIRED_ORDERS);
        PARAM_TO_KEY = Collections.unmodifiableMap(map);
    }

    @Override
    public Map<String, UiStateKey> getParamToKeyMappings() {
        return PARAM_TO_KEY;
    }
}
