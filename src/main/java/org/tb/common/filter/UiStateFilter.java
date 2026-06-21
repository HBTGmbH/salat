package org.tb.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;
import org.tb.common.web.UiState;
import org.tb.common.web.UiStateKeyRegistry;

@RequiredArgsConstructor
public class UiStateFilter extends OncePerRequestFilter {

    static final String COOKIE_PREFIX = "salat_";

    private final UiState uiState;
    private final UiStateKeyRegistry uiStateKeyRegistry;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
            FilterChain chain) throws ServletException, IOException {

        // Phase 1: explicit request params take precedence — update cookie and bean
        uiStateKeyRegistry.getParamToKey().forEach((param, key) -> {
            String raw = req.getParameter(param);
            if (raw != null && !raw.isBlank()) {
                uiState.setValue(key, raw);
                writeCookie(res, key.getName(), raw);
            }
        });

        // Phase 2: fill still-unset slots from existing cookies
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (c.getName().startsWith(COOKIE_PREFIX)) {
                    String keyName = c.getName().substring(COOKIE_PREFIX.length());
                    uiStateKeyRegistry.findByName(keyName).ifPresent(key -> {
                        if (uiState.getValue(key) == null) {
                            uiState.setValue(key, c.getValue());
                        }
                    });
                }
            }
        }

        chain.doFilter(req, res);
    }

    private void writeCookie(HttpServletResponse res, String keyName, String value) {
        // Use Set-Cookie header directly: Servlet API < 6 has no SameSite support on Cookie class
        res.addHeader("Set-Cookie",
            COOKIE_PREFIX + keyName + "=" + value + "; Path=/; HttpOnly; SameSite=Strict");
    }
}
