package org.tb.order.controller;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.tb.common.web.UiStateKey;
import org.tb.common.web.UiStateKeyContributor;

@Component
public class OrderUiStateKeyContributor implements UiStateKeyContributor {

    public static final UiStateKey SELECTED_CUSTOMER = new UiStateKey("selectedCustomer");
    public static final UiStateKey SELECTED_ORDER    = new UiStateKey("selectedOrder");
    public static final UiStateKey SELECTED_SUBORDER = new UiStateKey("selectedSuborder");

    @Override
    public Map<String, UiStateKey> getParamToKeyMappings() {
        return Map.of(
            "customerId",  SELECTED_CUSTOMER,
            "orderId",     SELECTED_ORDER,
            "suborderId",  SELECTED_SUBORDER
        );
    }
}
