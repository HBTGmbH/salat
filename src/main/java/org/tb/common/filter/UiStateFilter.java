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
import org.tb.common.web.UiStateKey;

@RequiredArgsConstructor
public class UiStateFilter extends OncePerRequestFilter {

    static final String COOKIE_PREFIX = "salat_";

    private final UiState uiState;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
            FilterChain chain) throws ServletException, IOException {

        // Phase 1: explicit request params take precedence — update cookie and bean
        UiStateKey.PARAM_TO_KEY.forEach((param, key) -> {
            String raw = req.getParameter(param);
            if (raw != null) {
                try {
                    long id = Long.parseLong(raw);
                    if (id > 0) {
                        uiState.setLong(key, id);
                        writeCookie(res, key, id);
                    }
                } catch (NumberFormatException ignored) {}
            }
        });

        // Phase 2: fill still-unset slots from existing cookies
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (c.getName().startsWith(COOKIE_PREFIX)) {
                    String key = c.getName().substring(COOKIE_PREFIX.length());
                    if (uiState.getLong(key) == null) {
                        try { uiState.setLong(key, Long.parseLong(c.getValue())); }
                        catch (NumberFormatException ignored) {}
                    }
                }
            }
        }

        chain.doFilter(req, res);
    }

    private void writeCookie(HttpServletResponse res, String key, long value) {
        // Use Set-Cookie header directly: Servlet API < 6 has no SameSite support on Cookie class
        res.addHeader("Set-Cookie",
            COOKIE_PREFIX + key + "=" + value + "; Path=/; HttpOnly; SameSite=Strict");
    }
}
