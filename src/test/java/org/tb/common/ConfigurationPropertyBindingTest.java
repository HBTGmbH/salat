package org.tb.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;

/**
 * A key in a profile that no {@code @ConfigurationProperties} field and no {@code @Value} reads is
 * dead: Spring binds relaxed and drops what nothing claims, so the application starts and the
 * default silently applies. Twice now that has hidden a real defect — {@code oauth2-redirect-url}
 * one level too deep in production (#1048), and a whole {@code salat.auth} branch in staging that
 * had grown apart from {@link SalatProperties} while the branch the code dereferences was missing
 * altogether (#1049, an NPE at startup).
 *
 * <p>The field names are read reflectively rather than written down here, so an upgrade that
 * renames a property makes this test fail instead of quietly passing.
 */
class ConfigurationPropertyBindingTest {

    /**
     * Prefix to the class that binds it. Longest prefix wins, so {@code springdoc.swagger-ui.oauth}
     * is resolved against {@link SwaggerUiOAuthProperties} and not against its parent. A prefix
     * absent from this map is not checked at all — the profiles also carry Spring's own settings,
     * and those are not this test's business.
     */
    private static final Map<String, Class<?>> BOUND_PREFIXES = new LinkedHashMap<>(Map.of(
        "springdoc.swagger-ui.oauth", SwaggerUiOAuthProperties.class,
        "springdoc.swagger-ui", SwaggerUiConfigProperties.class,
        "salat", SalatProperties.class));

    private static final Path SOURCES = Path.of("src/main/java");

    /** {@code @Value("${salat.cache.max-entries}")} or {@code ...:with-a-default}. */
    private static final Pattern VALUE_PLACEHOLDER =
        Pattern.compile("\\$\\{([A-Za-z0-9._-]+)\\s*(?::[^}]*)?}");

    @Test
    void everyProfileKeyIsReadBySomething() {
        Set<String> readByValue = placeholdersInSources();

        List<String> dead = new ArrayList<>();
        for (ProfileProperties.Entry entry : ProfileProperties.all()) {
            String prefix = longestBoundPrefix(entry.name());
            if (prefix == null) {
                continue;
            }
            String path = entry.name().substring(prefix.length() + 1);
            if (binds(BOUND_PREFIXES.get(prefix), segments(path))) {
                continue;
            }
            if (readByValue.contains(entry.name())) {
                continue;  // read directly, not through a properties class
            }
            dead.add(entry.toString());
        }

        assertThat(dead)
            .describedAs("""
                every key under a bound prefix reaches a field of its @ConfigurationProperties \
                class, or is read by an @Value placeholder. A key that reaches neither has no \
                effect and no error — see #1048 and #1049.""")
            .isEmpty();
    }

    /** The longest registered prefix this property sits under, or {@code null} for none. */
    private static String longestBoundPrefix(String property) {
        String best = null;
        for (String prefix : BOUND_PREFIXES.keySet()) {
            if (property.startsWith(prefix + ".")
                && (best == null || prefix.length() > best.length())) {
                best = prefix;
            }
        }
        return best;
    }

    private static List<String> segments(String path) {
        return List.of(path.split("\\."));
    }

    /** Walks the path field by field; a map swallows whatever is below it, a leaf ends the walk. */
    private static boolean binds(Class<?> type, List<String> segments) {
        if (segments.isEmpty()) {
            return true;
        }
        String head = segments.getFirst();
        int bracket = head.indexOf('[');
        Field field = findField(type, bracket < 0 ? head : head.substring(0, bracket));
        if (field == null) {
            return false;
        }
        List<String> rest = segments.subList(1, segments.size());
        Class<?> fieldType = field.getType();
        if (Map.class.isAssignableFrom(fieldType)) {
            return true;
        }
        if (Collection.class.isAssignableFrom(fieldType) || isLeaf(fieldType)) {
            return rest.isEmpty();
        }
        return binds(fieldType, rest);
    }

    /** Binding is relaxed: {@code oauth2-redirect-url} is the field {@code oauth2RedirectUrl}. */
    private static String relaxed(String name) {
        return name.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (!field.isSynthetic()
                    && !Modifier.isStatic(field.getModifiers())
                    && relaxed(field.getName()).equals(relaxed(name))) {
                    return field;
                }
            }
        }
        return null;
    }

    private static boolean isLeaf(Class<?> type) {
        return type.isPrimitive() || type.isEnum() || type.getName().startsWith("java.");
    }

    private static Set<String> placeholdersInSources() {
        Set<String> names = new TreeSet<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher matcher = VALUE_PLACEHOLDER.matcher(
                    Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    names.add(matcher.group(1));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + SOURCES, e);
        }
        return names;
    }
}
