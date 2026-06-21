package org.tb.order.controller;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.tb.common.web.UiStateKeyContributor;

@Component
public class OrderUiStateKeyContributor implements UiStateKeyContributor {

    public static final String SELECTED_CUSTOMER = "selectedCustomer";
    public static final String SELECTED_ORDER    = "selectedOrder";
    public static final String SELECTED_SUBORDER = "selectedSuborder";

    @Override
    public Map<String, String> getParamToKeyMappings() {
        return Map.of(
            "customerId",  SELECTED_CUSTOMER,
            "orderId",     SELECTED_ORDER,
            "suborderId",  SELECTED_SUBORDER
        );
    }
}
