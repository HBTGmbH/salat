package de.hbt.salat.budget.controller;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.web.UiStateKey;
import de.hbt.salat.common.web.UiStateKeyContributor;

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
     * The customer order of the plan list, of the rate list, of the flat-rate list and of the
     * controlling filter — deliberately one key for all of them (#952, #1009). Whoever picks an order
     * in one of them finds it preselected in the others.
     *
     * <p>By id, not by sign: a sign can be changed (ADR-0034), and a remembered or linked sign would
     * then select nothing (#1334). The parameter is not the order module's {@code fCustomerOrderId}:
     * the mapping is global, and the budget filter would otherwise travel into the suborder and
     * employee order lists, where an empty selection means "all" instead of "none". The cookie key is
     * new as well — a value remembered under the old {@code budget.CustomerorderSign} is a sign, and
     * the filter drops a key it does not know instead of reading it as an id. Links that still carry
     * {@code fCustomerOrderSign} are translated by {@link LegacyBudgetFilterController}.
     */
    public static final UiStateKey CUSTOMER_ORDER_ID = new UiStateKey("budget.CustomerorderId");

    /**
     * The three "show inactive" switches stay apart: in the plan list the switch means inactive
     * budget plans, in the rate list expired rates, in the flat-rate list expired flat rates (#1098).
     * Because the parameter mapping is global, telling them apart requires one parameter name each —
     * hence the prefixes.
     */
    public static final UiStateKey BUDGET_SHOW_INACTIVE = new UiStateKey("budgetList.ShowInactive");
    public static final UiStateKey PRICING_SHOW_INACTIVE = new UiStateKey("pricingList.ShowInactive");
    public static final UiStateKey FLAT_RATE_SHOW_INACTIVE = new UiStateKey("flatRateList.ShowInactive");

    /**
     * Whether the rate list, respectively the flat-rate list, also shows the entries of orders whose
     * validity has expired (#957, #1098) — a switch of its own on each list, because it is about the
     * order's validity and not the rate's.
     */
    public static final UiStateKey PRICING_SHOW_INACTIVE_ORDERS = new UiStateKey("pricingList.ShowInactiveOrders");
    public static final UiStateKey FLAT_RATE_SHOW_INACTIVE_ORDERS = new UiStateKey("flatRateList.ShowInactiveOrders");

    private static final Map<String, UiStateKey> PARAM_TO_KEY;
    static {
        var map = new HashMap<String, UiStateKey>();
        map.put("fBudgetSegmentId", DASHBOARD_SEGMENT_ID);
        map.put("fBudgetResponsibleId", DASHBOARD_RESPONSIBLE_ID);
        map.put("fBudgetCustomerOrderId", CUSTOMER_ORDER_ID);
        map.put("fBudgetShowInactive", BUDGET_SHOW_INACTIVE);
        map.put("fPricingShowInactive", PRICING_SHOW_INACTIVE);
        map.put("fPricingShowInactiveOrders", PRICING_SHOW_INACTIVE_ORDERS);
        map.put("fFlatRateShowInactive", FLAT_RATE_SHOW_INACTIVE);
        map.put("fFlatRateShowInactiveOrders", FLAT_RATE_SHOW_INACTIVE_ORDERS);
        PARAM_TO_KEY = Collections.unmodifiableMap(map);
    }

    @Override
    public Map<String, UiStateKey> getParamToKeyMappings() {
        return PARAM_TO_KEY;
    }
}
