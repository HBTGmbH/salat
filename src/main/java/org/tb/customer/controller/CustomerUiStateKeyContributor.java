package org.tb.customer.controller;

import org.springframework.stereotype.Component;
import org.tb.common.web.UiStateKey;
import org.tb.common.web.UiStateKeyContributor;

import java.util.Map;

import static java.util.Map.of;

@Component
public class CustomerUiStateKeyContributor implements UiStateKeyContributor {

    public static final UiStateKey FILTER = new UiStateKey("filter");
    public static final UiStateKey SHOW_HIDDEN = new UiStateKey("showHidden");

    @Override
    public Map<String, UiStateKey> getParamToKeyMappings() {
        return of(
                "filter", FILTER,
                "showHidden", SHOW_HIDDEN
        );
    }
}
