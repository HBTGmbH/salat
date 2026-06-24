package org.tb.order.controller;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.tb.common.web.UiStateKey;
import org.tb.common.web.UiStateKeyContributor;

@Component
public class OrderUiStateKeyContributor implements UiStateKeyContributor {

    public static final UiStateKey CUSTOMER_ID = new UiStateKey("customer.Id");
    public static final UiStateKey CUSTOMER_ORDER_ID = new UiStateKey("customerOrder.Id");
    public static final UiStateKey CUSTOMER_ORDER_FILTER = new UiStateKey("customerOrder.Filter");
    public static final UiStateKey CUSTOMER_ORDER_SHOW_INVALID = new UiStateKey("customerOrder.ShowInvalid");
    public static final UiStateKey CUSTOMER_ORDER_SHOW_HIDDEN = new UiStateKey("customerOrder.ShowHidden");
    public static final UiStateKey CUSTOMER_ORDER_SHOW_ACTUAL_HOURS = new UiStateKey("customerOrder.ShowActualHours");
    public static final UiStateKey SUBORDER_ID = new UiStateKey("suborder.Id");

    private static final Map<String, UiStateKey> PARAM_TO_KEY;
    static {
        var map = new HashMap<String, UiStateKey>();
        map.put("customerId", CUSTOMER_ID);
        map.put("orderId", CUSTOMER_ORDER_ID);
        map.put("customerOrderId", CUSTOMER_ORDER_ID);
        map.put("suborderId", SUBORDER_ID);
        map.put("coFilter", CUSTOMER_ORDER_FILTER);
        map.put("coShowInvalid", CUSTOMER_ORDER_SHOW_INVALID);
        map.put("coShowHidden", CUSTOMER_ORDER_SHOW_HIDDEN);
        map.put("coShowActualHours", CUSTOMER_ORDER_SHOW_ACTUAL_HOURS);
        PARAM_TO_KEY = Collections.unmodifiableMap(map);
    }

    @Override
    public Map<String, UiStateKey> getParamToKeyMappings() {
        return PARAM_TO_KEY;
    }
}
