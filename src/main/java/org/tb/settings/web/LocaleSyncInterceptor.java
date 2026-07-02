package org.tb.settings.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.util.WebUtils;
import org.tb.settings.service.UiPreferenceService;

@Component
@RequiredArgsConstructor
public class LocaleSyncInterceptor implements HandlerInterceptor {

    // Sentinel stored in the cookie when the user chose "browser detection".
    // CookieLocaleResolver rejectInvalidCookies=false silently ignores this
    // unparseable value and falls back to Accept-Language — exactly what we want.
    static final String AUTO = "auto";

    private final UiPreferenceService uiPreferenceService;
    private final CookieLocaleResolver localeResolver;

    @Value("${salat.locale-cookie-name}")
    private String cookieName;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!isAuthenticated()) return true;
        if (WebUtils.getCookie(request, cookieName) != null) return true;

        String savedLocale = uiPreferenceService.getLocaleForCurrentUser();
        Locale locale = switch (savedLocale) {
            case "de" -> Locale.GERMAN;
            case "en" -> Locale.ENGLISH;
            default -> null;
        };
        if (locale != null) {
            localeResolver.setLocale(request, response, locale);
        } else {
            writeSentinelCookie(response);
        }
        return true;
    }

    public void writeSentinelCookie(HttpServletResponse response) {
        var cookie = new Cookie(cookieName, AUTO);
        cookie.setPath("/");
        cookie.setMaxAge(365 * 24 * 3600);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
    }

    private static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }

}
