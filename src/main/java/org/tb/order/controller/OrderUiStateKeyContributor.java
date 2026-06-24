package org.tb.order.controller;

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

    @Override
    public Map<String, UiStateKey> getParamToKeyMappings() {
        return Map.of(
            "customerId", CUSTOMER_ID,
            "orderId", CUSTOMER_ORDER_ID,
            "suborderId", SUBORDER_ID,
            "coFilter", CUSTOMER_ORDER_FILTER,
            "coShowInvalid", CUSTOMER_ORDER_SHOW_INVALID,
            "coShowHidden", CUSTOMER_ORDER_SHOW_HIDDEN,
            "coShowActualHours", CUSTOMER_ORDER_SHOW_ACTUAL_HOURS
        );
    }
}
