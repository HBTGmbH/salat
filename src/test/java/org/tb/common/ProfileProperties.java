package org.tb.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

/**
 * Every property the checked-in profiles set, flattened the way Spring Boot sees it — {@code
 * salat.auth.easy-auth.oidc-id-token.header-name} rather than a nested map. Reading the files
 * instead of starting a context is deliberate: the profiles that matter here (production, staging,
 * localeasyauth) cannot be booted in a test, and it is the file that is wrong when it is wrong.
 */
final class ProfileProperties {

    private static final Path RESOURCES = Path.of("src/main/resources");

    private ProfileProperties() {
    }

    /** One property of one profile file. {@code file} is the file name, for readable failures. */
    record Entry(String file, String name) {

        @Override
        public String toString() {
            return name + " (" + file + ")";
        }
    }

    static List<Entry> all() {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<Entry> entries = new ArrayList<>();
        for (Path file : profileFiles()) {
            String fileName = file.getFileName().toString();
            List<PropertySource<?>> sources;
            try {
                sources = loader.load(fileName, new FileSystemResource(file));
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + file, e);
            }
            for (PropertySource<?> source : sources) {
                for (String name : ((EnumerablePropertySource<?>) source).getPropertyNames()) {
                    entries.add(new Entry(fileName, name));
                }
            }
        }
        return entries;
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
}
