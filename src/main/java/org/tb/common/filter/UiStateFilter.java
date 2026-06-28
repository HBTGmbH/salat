package org.tb.common.filter;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.tb.common.SalatProperties;
import org.tb.common.web.LoginSignProvider;
import org.tb.common.web.SensitiveUiStateKey;
import org.tb.common.web.UiState;
import org.tb.common.web.UiStateKey;
import org.tb.common.web.UiStateKeyRegistry;

@Component
@RequiredArgsConstructor
@Order(102)
public class UiStateFilter extends OncePerRequestFilter {

    static final String COOKIE_NAME = "salat_uistate";
    static final String COOKIE_KEY_LOGIN_SIGN = "_ls";

    private static final AntPathMatcher ANT = new AntPathMatcher();
    private static final List<String> STATIC_PATTERNS = List.of(
        "/images/**", "/webjars/**",
        "/**/*.css", "/**/*.js",
        "/**/*.gif", "/**/*.png", "/**/*.jpg", "/**/*.jpeg",
        "/**/*.svg", "/**/*.ico",
        "/**/*.woff", "/**/*.woff2", "/**/*.ttf", "/**/*.eot",
        "/**/*.map", "/**/*.webp");

    private final UiState uiState;
    private final UiStateKeyRegistry uiStateKeyRegistry;
    private final LoginSignProvider loginSignProvider;
    private final SalatProperties salatProperties;

    private byte[] signingKeyBytes;

    @PostConstruct
    void initSigningKey() {
        String configured = salatProperties.getUiState() != null
            ? salatProperties.getUiState().getSigningKey() : null;
        String key = (configured != null && !configured.isBlank())
            ? configured : UUID.randomUUID().toString();
        signingKeyBytes = key.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return STATIC_PATTERNS.stream().anyMatch(p -> ANT.match(p, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
            FilterChain chain) throws ServletException, IOException {

        var dirty = false;

        // Phase 1: fill slots from the cookie, but only when the stored
        // login sign matches the current effective login sign (guards against stale state
        // left behind by a previous user). At this point UiState is empty, so
        // getEffectiveLoginSign() returns the base login sign from SecurityContextHolder,
        // which is what we store in _ls (not the impersonated sign).
        String effectiveLoginSign = loginSignProvider.getEffectiveLoginSign();
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (COOKIE_NAME.equals(c.getName())) {
                    Map<String, String> parsed = parseCookieValue(c.getValue());
                    String storedLoginSign = parsed.get(COOKIE_KEY_LOGIN_SIGN);
                    if (effectiveLoginSign != null) {
                        if (effectiveLoginSign.equals(storedLoginSign)) {
                            parsed.forEach((keyName, value) -> {
                                if (keyName.startsWith("_sig_")) return;
                                uiStateKeyRegistry.findByName(keyName).ifPresent(key -> {
                                    if (key instanceof SensitiveUiStateKey) {
                                        String storedSig = parsed.get("_sig_" + keyName);
                                        if (storedSig == null) return;
                                        byte[] expected = computeHmacBytes(keyName, value, storedLoginSign);
                                        byte[] actual = Base64.getUrlDecoder().decode(storedSig);
                                        if (!MessageDigest.isEqual(expected, actual)) return;
                                    }
                                    uiState.setValue(key, value);
                                });
                            });
                        } else {
                            dirty = true;
                        }
                    }
                    break;
                }
            }
        }

        // Phase 2: explicit request params take precedence (override cookie value)
        for (var entry : uiStateKeyRegistry.getParamToKey().entrySet()) {
            var param = entry.getKey();
            var key = entry.getValue();
            String raw = req.getParameter(param);
            if (raw != null) {
                dirty |= uiState.setValue(key, raw);
            }
        }

        Map<UiStateKey, String> stateBeforeChain = new HashMap<>(uiState.getAll());
        var bufferingRes = new BufferingResponseWrapper(res);

        // Phase 3: expose UiState values as fallback request parameters
        Map<String, String> fallbacks = new java.util.HashMap<>();
        uiStateKeyRegistry.getParamToKey().forEach((paramName, key) -> {
            String value = uiState.getValue(key);
            if (value != null) fallbacks.put(paramName, value);
        });
        HttpServletRequest wrappedReq = fallbacks.isEmpty() ? req
            : new UiStateParameterRequestWrapper(req, fallbacks);

        chain.doFilter(wrappedReq, bufferingRes);

        // Write cookie after the chain so any UiState mutations (e.g. clearState) are captured.
        // Always write when state changed (including to empty) so the browser cookie is cleared.
        Map<UiStateKey, String> stateAfterChain = uiState.getAll();
        if ((dirty || !stateAfterChain.equals(stateBeforeChain)) && effectiveLoginSign != null) {
            writeCookie(bufferingRes, buildCookieValue(stateAfterChain, effectiveLoginSign));
        }
        bufferingRes.writeAndCommit();
    }

    private String buildCookieValue(Map<UiStateKey, String> all, String loginSign) {
        StringBuilder sb = new StringBuilder();
        if (loginSign != null) {
            sb.append(COOKIE_KEY_LOGIN_SIGN).append("=").append(loginSign);
        }
        for (Map.Entry<UiStateKey, String> entry : all.entrySet()) {
            String keyName = entry.getKey().getName();
            String value = entry.getValue();
            if (sb.length() > 0) sb.append("&");
            sb.append(keyName).append("=").append(value);
            if (entry.getKey() instanceof SensitiveUiStateKey) {
                String hmac = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(computeHmacBytes(keyName, value, loginSign));
                sb.append("&_sig_").append(keyName).append("=").append(hmac);
            }
        }
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
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

    private byte[] computeHmacBytes(String keyName, String value, String loginSign) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKeyBytes, "HmacSHA256"));
            String message = COOKIE_KEY_LOGIN_SIGN + "=" + loginSign + "&" + keyName + "=" + value;
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    private void writeCookie(HttpServletResponse res, String value) {
        // Use Set-Cookie header directly: Servlet API < 6 has no SameSite support on Cookie class
        res.addHeader("Set-Cookie",
            COOKIE_NAME + "=" + value + "; Path=/; HttpOnly; SameSite=Strict");
    }

    private static class BufferingResponseWrapper extends ContentCachingResponseWrapper {

        private String deferredRedirectLocation;
        private Integer deferredErrorStatus;
        private String deferredErrorMessage;

        BufferingResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void sendRedirect(String location) {
            this.deferredRedirectLocation = location;
        }

        @Override
        public void sendError(int sc) {
            this.deferredErrorStatus = sc;
        }

        @Override
        public void sendError(int sc, String msg) {
            this.deferredErrorStatus = sc;
            this.deferredErrorMessage = msg;
        }

        @Override
        public void flushBuffer() {
            // Suppress premature flush — body and headers are committed by writeAndCommit().
        }

        void writeAndCommit() throws IOException {
            if (deferredRedirectLocation != null) {
                super.sendRedirect(deferredRedirectLocation);
            } else if (deferredErrorStatus != null) {
                if (deferredErrorMessage != null) {
                    super.sendError(deferredErrorStatus, deferredErrorMessage);
                } else {
                    super.sendError(deferredErrorStatus);
                }
            } else {
                copyBodyToResponse();
            }
        }
    }
}
