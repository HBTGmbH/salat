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

    private final Map<UiStateKey, String> values = new HashMap<>();

    public String getValue(UiStateKey key) {
        return values.get(key);
    }

    public Long getLongValue(UiStateKey key) {
        String raw = values.get(key);
        return raw != null ? Long.parseLong(raw) : null;
    }

    public void setValue(UiStateKey key, String value) {
        values.put(key, value);
    }

    public void setLong(UiStateKey key, Long value) {
        setValue(key, String.valueOf(value));
    }

    public Map<UiStateKey, String> getAll() {
        return Collections.unmodifiableMap(values);
    }
}
