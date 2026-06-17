package org.tb.common.web;

import java.util.Map;

public final class UiStateKey {

    public static final String SELECTED_CONTRACT = "selectedContract";
    public static final String SELECTED_CUSTOMER = "selectedCustomer";
    public static final String SELECTED_ORDER    = "selectedOrder";
    public static final String SELECTED_SUBORDER = "selectedSuborder";

    /** Maps HTTP request-param name → UI state key. */
    public static final Map<String, String> PARAM_TO_KEY = Map.of(
        "employeeContractId", SELECTED_CONTRACT,
        "customerId",         SELECTED_CUSTOMER,
        "orderId",            SELECTED_ORDER,
        "suborderId",         SELECTED_SUBORDER
    );

    private UiStateKey() {}
}
