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
    public static final UiStateKey CUSTOMER_ORDER_SHOW_INACTIVE = new UiStateKey("customerOrder.ShowInactive");
    public static final UiStateKey CUSTOMER_ORDER_SHOW_HIDDEN = new UiStateKey("customerOrder.ShowHidden");
    public static final UiStateKey CUSTOMER_ORDER_SHOW_ACTUAL_HOURS = new UiStateKey("customerOrder.ShowActualHours");
    public static final UiStateKey SUBORDER_ID = new UiStateKey("suborder.Id");
    public static final UiStateKey SUBORDER_FILTER = new UiStateKey("suborder.Filter");
    public static final UiStateKey SUBORDER_SHOW_INACTIVE = new UiStateKey("suborder.ShowInactive");
    public static final UiStateKey SUBORDER_SHOW_HIDDEN = new UiStateKey("suborder.ShowHidden");
    public static final UiStateKey SUBORDER_SHOW_ACTUAL_HOURS = new UiStateKey("suborder.ShowActualHours");
    public static final UiStateKey EMPLOYEEORDER_EMPLOYEE_CONTRACT_ID = new UiStateKey("employeeOrder.EmployeeContract.Id");
    public static final UiStateKey EMPLOYEEORDER_FILTER = new UiStateKey("employeeOrder.Filter");
    public static final UiStateKey EMPLOYEEORDER_SHOW_INACTIVE = new UiStateKey("employeeOrder.ShowInactive");
    public static final UiStateKey EMPLOYEEORDER_SHOW_HIDDEN = new UiStateKey("employeeOrder.ShowHidden");
    public static final UiStateKey EMPLOYEEORDER_SHOW_ACTUAL_HOURS = new UiStateKey("employeeOrder.ShowActualHours");

    private static final Map<String, UiStateKey> PARAM_TO_KEY;
    static {
        var map = new HashMap<String, UiStateKey>();
        map.put("fCustomerId", CUSTOMER_ID);
        map.put("fCustomerOrderId", CUSTOMER_ORDER_ID);
        map.put("fSuborderId", SUBORDER_ID);
        map.put("fCustomerOrderFilter", CUSTOMER_ORDER_FILTER);
        map.put("fCustomerOrderShowInactive", CUSTOMER_ORDER_SHOW_INACTIVE);
        map.put("fCustomerOrderShowHidden", CUSTOMER_ORDER_SHOW_HIDDEN);
        map.put("fCustomerOrderShowActualHours", CUSTOMER_ORDER_SHOW_ACTUAL_HOURS);
        map.put("fSuborderFilter", SUBORDER_FILTER);
        map.put("fSuborderShowInactive", SUBORDER_SHOW_INACTIVE);
        map.put("fSuborderShowHidden", SUBORDER_SHOW_HIDDEN);
        map.put("fSuborderShowActualHours", SUBORDER_SHOW_ACTUAL_HOURS);
        map.put("fEmployeeOrderEmployeeContractId", EMPLOYEEORDER_EMPLOYEE_CONTRACT_ID);
        map.put("fEmployeeOrderFilter", EMPLOYEEORDER_FILTER);
        map.put("fEmployeeOrderShowInactive", EMPLOYEEORDER_SHOW_INACTIVE);
        map.put("fEmployeeOrderShowHidden", EMPLOYEEORDER_SHOW_HIDDEN);
        map.put("fEmployeeOrderShowActualHours", EMPLOYEEORDER_SHOW_ACTUAL_HOURS);
        PARAM_TO_KEY = Collections.unmodifiableMap(map);
    }

    @Override
    public Map<String, UiStateKey> getParamToKeyMappings() {
        return PARAM_TO_KEY;
    }
}
