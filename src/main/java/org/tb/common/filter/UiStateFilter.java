package org.tb.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.tb.common.web.LoginSignProvider;
import org.tb.common.web.UiState;
import org.tb.common.web.UiStateKey;
import org.tb.common.web.UiStateKeyRegistry;

@Component
@RequiredArgsConstructor
@Order(102)
public class UiStateFilter extends OncePerRequestFilter {

    static final String COOKIE_NAME = "salat_uistate";
    static final String COOKIE_KEY_LOGIN_SIGN = "_ls";

    private final UiState uiState;
    private final UiStateKeyRegistry uiStateKeyRegistry;
    private final LoginSignProvider loginSignProvider;

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

        // Phase 2: fill still-unset slots from the merged cookie, but only when the stored
        // login sign matches the current effective login sign (guards against stale state
        // left behind by a previous user or an impersonation switch).
        String effectiveLoginSign = loginSignProvider.getEffectiveLoginSign();
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (COOKIE_NAME.equals(c.getName())) {
                    Map<String, String> parsed = parseCookieValue(c.getValue());
                    String storedLoginSign = parsed.get(COOKIE_KEY_LOGIN_SIGN);
                    if (effectiveLoginSign != null && effectiveLoginSign.equals(storedLoginSign)) {
                        parsed.forEach((keyName, value) ->
                            uiStateKeyRegistry.findByName(keyName).ifPresent(key -> {
                                if (uiState.getValue(key) == null) {
                                    uiState.setValue(key, value);
                                }
                            })
                        );
                    }
                    break;
                }
            }
        }

        // Write the merged cookie with the full current state
        Map<UiStateKey, String> all = uiState.getAll();
        if (!all.isEmpty()) {
            writeCookie(res, buildCookieValue(all, effectiveLoginSign));
        }

        chain.doFilter(req, res);
    }

    private String buildCookieValue(Map<UiStateKey, String> all, String loginSign) {
        String statePart = all.entrySet().stream()
            .map(e -> e.getKey().getName() + "=" + e.getValue())
            .collect(Collectors.joining("&"));
        String plain = loginSign != null
            ? COOKIE_KEY_LOGIN_SIGN + "=" + loginSign + "&" + statePart
            : statePart;
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, String> parseCookieValue(String raw) {
        Map<String, String> result = new HashMap<>();
        try {
            String plain = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
            for (String pair : plain.split("&")) {
                int idx = pair.indexOf('=');
                if (idx > 0) {
                    result.put(pair.substring(0, idx), pair.substring(idx + 1));
                }
            }
        } catch (IllegalArgumentException ignored) {}
        return result;
    }

    private void writeCookie(HttpServletResponse res, String value) {
        // Use Set-Cookie header directly: Servlet API < 6 has no SameSite support on Cookie class
        res.addHeader("Set-Cookie",
            COOKIE_NAME + "=" + value + "; Path=/; HttpOnly; SameSite=Strict");
    }
}
