package org.tb.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

/**
 * Springdoc splits its Swagger UI settings over two {@code @ConfigurationProperties} classes:
 * {@link SwaggerUiConfigProperties} binds {@code springdoc.swagger-ui}, {@link
 * SwaggerUiOAuthProperties} binds {@code springdoc.swagger-ui.oauth}. A key placed under the wrong
 * one is not an error — Spring Boot binds relaxed and drops what nothing reads, so the application
 * starts and the default silently applies.
 *
 * <p>That is how {@code oauth2-redirect-url} came to sit one level too deep in production while
 * staging had it right (#1048): Swagger UI announced the default redirect URI, which is not among
 * those registered, and "Authorize" failed with a redirect-URI mismatch — visible only in the
 * browser, against production.
 */
class SpringdocConfigurationTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final String SWAGGER_UI = "springdoc.swagger-ui.";
    private static final String OAUTH = "oauth.";

    /** Field names bind relaxed: {@code oauth2-redirect-url} is {@code oauth2RedirectUrl}. */
    private static String relaxed(String name) {
        return name.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
    }

    private static Set<String> bindableNames(Class<?> type) {
        Set<String> names = new TreeSet<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            Stream.of(c.getDeclaredFields())
                .filter(f -> !f.isSynthetic())
                .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .forEach(f -> names.add(relaxed(f.getName())));
        }
        return names;
    }

    /** The first path segment of {@code csrf.enabled} or {@code scopes[0]} is what has to bind. */
    private static String firstSegment(String propertyPath) {
        String head = propertyPath.split("\\.", 2)[0];
        int bracket = head.indexOf('[');
        return bracket < 0 ? head : head.substring(0, bracket);
    }

    /** Every {@code springdoc.*} property of every profile, mapped to the file it comes from. */
    private static Map<String, String> springdocProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (Path file : profileFiles()) {
            List<PropertySource<?>> sources;
            try {
                sources = loader.load(file.getFileName().toString(), new FileSystemResource(file));
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + file, e);
            }
            for (PropertySource<?> source : sources) {
                for (String name : ((EnumerablePropertySource<?>) source).getPropertyNames()) {
                    if (name.startsWith("springdoc.")) {
                        properties.put(name + " (" + file.getFileName() + ")", name);
                    }
                }
            }
        }
        return properties;
    }

    private static List<Path> profileFiles() {
        try (Stream<Path> files = Files.list(RESOURCES)) {
            return files
                .filter(p -> p.getFileName().toString().matches("application(-[a-z-]+)?\\.ya?ml"))
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + RESOURCES, e);
        }
    }

    @Test
    void everySwaggerUiPropertyBindsToAPropertiesClass() {
        Set<String> uiFields = bindableNames(SwaggerUiConfigProperties.class);
        Set<String> oauthFields = bindableNames(SwaggerUiOAuthProperties.class);

        List<String> unbindable = new ArrayList<>();
        springdocProperties().forEach((described, name) -> {
            if (!name.startsWith(SWAGGER_UI)) {
                return;
            }
            String rest = name.substring(SWAGGER_UI.length());
            boolean bound = rest.startsWith(OAUTH)
                ? oauthFields.contains(relaxed(firstSegment(rest.substring(OAUTH.length()))))
                : "oauth".equals(firstSegment(rest)) || uiFields.contains(relaxed(firstSegment(rest)));
            if (!bound) {
                unbindable.add(described);
            }
        });

        assertThat(unbindable)
            .describedAs("""
                springdoc.swagger-ui.* binds to SwaggerUiConfigProperties, \
                springdoc.swagger-ui.oauth.* to SwaggerUiOAuthProperties. \
                A key neither knows is dropped without a word — see #1048.""")
            .isEmpty();
    }

    @Test
    void profilesConfiguringSwaggerOAuthAlsoSetTheRedirectUrl() {
        Map<String, String> properties = springdocProperties();
        Set<String> withOAuth = new TreeSet<>();
        Set<String> withRedirectUrl = new TreeSet<>();
        properties.forEach((described, name) -> {
            String file = described.substring(described.indexOf('(') + 1, described.length() - 1);
            if (name.equals(SWAGGER_UI + "oauth.client-id")) {
                withOAuth.add(file);
            }
            if (name.equals(SWAGGER_UI + "oauth2-redirect-url")) {
                withRedirectUrl.add(file);
            }
        });

        assertThat(withOAuth)
            .describedAs("the profiles under test actually configure Swagger UI OAuth")
            .isNotEmpty();
        assertThat(withRedirectUrl)
            .describedAs("""
                a profile that configures Swagger UI OAuth names the redirect URI as well: \
                the springdoc default points at /swagger-ui/, not at the configured UI path, \
                and is not registered with the identity provider (#1048)""")
            .containsAll(withOAuth);
    }
}
