package de.hbt.salat.customer.controller;

import org.springframework.stereotype.Component;
import de.hbt.salat.common.web.UiStateKey;
import de.hbt.salat.common.web.UiStateKeyContributor;

import java.util.Map;

import static java.util.Map.of;

@Component
public class CustomerUiStateKeyContributor implements UiStateKeyContributor {

    public static final UiStateKey CUSTOMER_FILTER = new UiStateKey("customer.Filter");
    public static final UiStateKey CUSTOMER_SHOW_HIDDEN = new UiStateKey("customer.ShowHidden");

    @Override
    public Map<String, UiStateKey> getParamToKeyMappings() {
        return of(
                "fCustomerFilter", CUSTOMER_FILTER,
                "fCustomerShowHidden", CUSTOMER_SHOW_HIDDEN
        );
    }
}
