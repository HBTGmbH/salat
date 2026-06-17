package org.tb.common.web;

import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class UiState {

    private final Map<String, Long> values = new HashMap<>();

    public Long getLong(String key) {
        return values.get(key);
    }

    public void setLong(String key, Long value) {
        values.put(key, value);
    }

    public Long getSelectedContractId() {
        return getLong(UiStateKey.SELECTED_CONTRACT);
    }
}
