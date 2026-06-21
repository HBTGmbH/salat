package org.tb.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;
import org.tb.common.web.UiState;
import org.tb.common.web.UiStateKey;
import org.tb.common.web.UiStateKeyRegistry;

@RequiredArgsConstructor
public class UiStateFilter extends OncePerRequestFilter {

    static final String COOKIE_NAME = "salat_uistate";

    private final UiState uiState;
    private final UiStateKeyRegistry uiStateKeyRegistry;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
            FilterChain chain) throws ServletException, IOException {

        // Phase 1: explicit request params take precedence
        uiStateKeyRegistry.getParamToKey().forEach((param, key) -> {
            String raw = req.getParameter(param);
            if (raw != null && !raw.isBlank()) {
                uiState.setValue(key, raw);
            }
        });

        // Phase 2: fill still-unset slots from the merged cookie
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (COOKIE_NAME.equals(c.getName())) {
                    parseCookieValue(c.getValue()).forEach((keyName, value) ->
                        uiStateKeyRegistry.findByName(keyName).ifPresent(key -> {
                            if (uiState.getValue(key) == null) {
                                uiState.setValue(key, value);
                            }
                        })
                    );
                    break;
                }
            }
        }

        // Write the merged cookie with the full current state
        Map<UiStateKey, String> all = uiState.getAll();
        if (!all.isEmpty()) {
            writeCookie(res, buildCookieValue(uiState.getAll()));
        }

        chain.doFilter(req, res);
    }

    private String buildCookieValue(Map<UiStateKey, String> all) {
        return all.entrySet().stream()
            .map(e -> e.getKey().getName() + "=" + e.getValue())
            .collect(Collectors.joining("&"));
    }

    private Map<String, String> parseCookieValue(String raw) {
        Map<String, String> result = new HashMap<>();
        for (String pair : raw.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                result.put(pair.substring(0, idx), pair.substring(idx + 1));
            }
        }
        return result;
    }

    private void writeCookie(HttpServletResponse res, String value) {
        // Use Set-Cookie header directly: Servlet API < 6 has no SameSite support on Cookie class
        res.addHeader("Set-Cookie",
            COOKIE_NAME + "=" + value + "; Path=/; HttpOnly; SameSite=Strict");
    }
}
