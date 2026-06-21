package org.tb.common.web;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class UiState {

    private final Map<UiStateKey, String> values = new HashMap<>();
    private boolean dirty = false;

    public String getValue(UiStateKey key) {
        return values.get(key);
    }

    public Long getLongValue(UiStateKey key) {
        String raw = values.get(key);
        return raw != null ? Long.parseLong(raw) : null;
    }

    /**
     * @return true if the value changed
     */
    public boolean setValue(UiStateKey key, String value) {
        return !Objects.equals(value, values.put(key, value));
    }

    public Map<UiStateKey, String> getAll() {
        return Collections.unmodifiableMap(values);
    }
}
