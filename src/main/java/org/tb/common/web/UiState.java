package org.tb.common.web;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class UiState {

    private final Map<UiStateKey, Long> values = new HashMap<>();

    public Long getLong(UiStateKey key) {
        return values.get(key);
    }

    public void setLong(UiStateKey key, Long value) {
        values.put(key, value);
    }

    public Map<UiStateKey, Long> getAll() {
        return Collections.unmodifiableMap(values);
    }
}
