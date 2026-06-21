package org.tb.common.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

class UiStateParameterRequestWrapper extends HttpServletRequestWrapper {

    private final Map<String, String> fallbackParams;

    UiStateParameterRequestWrapper(HttpServletRequest request, Map<String, String> fallbackParams) {
        super(request);
        this.fallbackParams = fallbackParams;
    }

    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        return value != null ? value : fallbackParams.get(name);
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values != null) return values;
        String fallback = fallbackParams.get(name);
        return fallback != null ? new String[]{fallback} : null;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> merged = new HashMap<>();
        fallbackParams.forEach((k, v) -> merged.put(k, new String[]{v}));
        merged.putAll(super.getParameterMap()); // actual params take precedence
        return Collections.unmodifiableMap(merged);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        Set<String> names = new LinkedHashSet<>(fallbackParams.keySet());
        Collections.list(super.getParameterNames()).forEach(names::add);
        return Collections.enumeration(names);
    }
}
