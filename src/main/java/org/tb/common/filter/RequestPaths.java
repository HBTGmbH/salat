package org.tb.common.filter;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.util.AntPathMatcher;

/**
 * Die beiden Pfadgruppen, die mehrere Filter dieser Anwendung gleich behandeln müssen.
 *
 * <p>Sie stehen hier, weil drei Filter dieselbe Liste brauchen und eine Kopie davon irgendwann von
 * den anderen abweicht: ein Dateityp, der nur in zwei von drei Listen steht, fällt in genau dem
 * Filter durch, in dem er fehlt.
 */
public final class RequestPaths {

    private static final AntPathMatcher ANT = new AntPathMatcher();

    /**
     * Statische Dateien. Sie tragen keinen Benutzerzustand und dürfen auch dann ausgeliefert werden,
     * wenn der Anfrage sonst nichts mehr beantwortet wird — die Fehlerseite lädt ihr Stylesheet über
     * denselben Weg wie jede andere Seite (#1054).
     */
    private static final List<String> STATIC_PATTERNS = List.of(
        "/images/**", "/webjars/**",
        "/**/*.css", "/**/*.js",
        "/**/*.gif", "/**/*.png", "/**/*.jpg", "/**/*.jpeg",
        "/**/*.svg", "/**/*.ico",
        "/**/*.woff", "/**/*.woff2", "/**/*.ttf", "/**/*.eot",
        "/**/*.map", "/**/*.webp");

    /**
     * Die zustandslosen Ketten für maschinelle Aufrufer. Was die Oberfläche sich merkt, gilt dort
     * nicht, und was sie als Seite beantwortet bekommt, ist dort eine HTML-Seite an einem Skript.
     */
    private static final List<String> STATELESS_PATTERNS = List.of("/api/**", "/rest/**");

    private RequestPaths() {
    }

    public static boolean isStaticResource(HttpServletRequest request) {
        return matchesAny(STATIC_PATTERNS, request);
    }

    public static boolean isStateless(HttpServletRequest request) {
        return matchesAny(STATELESS_PATTERNS, request);
    }

    private static boolean matchesAny(List<String> patterns, HttpServletRequest request) {
        String path = request.getServletPath();
        return patterns.stream().anyMatch(pattern -> ANT.match(pattern, path));
    }

}
