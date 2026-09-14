package org.tb.common.web;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeJars;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * The rule of ADR-0022: a UiState parameter is recognisable as one, and nothing else carries its
 * name. {@link org.tb.common.filter.UiStateFilter} puts every remembered value under the request as
 * a fallback parameter, so a name shared with a form field feeds that form a value nobody entered —
 * {@code month} did exactly that to the CSV export and to every hidden field of the daily view
 * (#999). The rule only holds as long as something checks it; that is what this test is.
 */
class UiStateParameterNamingTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    /** {@code th:field="*{customerId}"} binds the field to a property of the bound object. */
    private static final Pattern TH_FIELD = Pattern.compile("th:field=\"\\*\\{([A-Za-z0-9_]+)");

    /** {@code name="month"} — the plain request-parameter name of an input. */
    private static final Pattern INPUT_NAME = Pattern.compile("\\bname=\"([A-Za-z0-9_]+)\"");

    private static final Pattern POST_FORM = Pattern.compile(
        "<form[^>]*method=\"post\"[^>]*>(.*?)</form>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    /** A URL ending in {@code /create} that hands over a parameter, in a template or in Java. */
    private static final Pattern CREATE_LINK = Pattern.compile("/create[^\"'\n]*?\\b(f[A-Z][A-Za-z0-9]*)=");

    private final Set<String> registeredParams = registry().getParamToKey().keySet();

    @Test
    void everyRegisteredParameterIsPrefixed() {
        assertThat(registeredParams)
            .describedAs("every UiState parameter is named f + capital letter (ADR-0022)")
            .isNotEmpty()
            .allMatch(name -> name.matches("^f[A-Z][A-Za-z0-9]*$"));
    }

    @Test
    void registeringAnUnprefixedParameterFails() {
        UiStateKeyContributor rogue = () -> Map.of("month", new UiStateKey("matrix.Month"));
        assertThatThrownBy(() -> new UiStateKeyRegistry(List.of(rogue)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("month");
    }

    /**
     * A bound form property with the name of a UiState parameter is the collision itself: the form
     * receives the remembered value wherever the request brings none of its own.
     */
    @Test
    void noRegisteredParameterIsBoundAsFormField() throws IOException {
        var offending = new ArrayList<String>();
        forEachTemplate((file, content) -> {
            var matcher = TH_FIELD.matcher(content);
            while (matcher.find()) {
                if (registeredParams.contains(matcher.group(1))) {
                    offending.add(file + ": th:field=\"*{" + matcher.group(1) + "}\"");
                }
            }
        });
        assertThat(offending)
            .describedAs("a UiState parameter must not be the name of a bound form property")
            .isEmpty();
    }

    /**
     * Submitting a form writes every request parameter the filter knows into the UiState. A POST
     * form carrying a UiState name would therefore change a filter as a side effect of saving.
     */
    @Test
    void noRegisteredParameterIsAFieldOfAPostForm() throws IOException {
        var offending = new ArrayList<String>();
        forEachTemplate((file, content) -> {
            Matcher form = POST_FORM.matcher(content);
            while (form.find()) {
                Matcher field = INPUT_NAME.matcher(form.group(1));
                while (field.find()) {
                    if (registeredParams.contains(field.group(1))) {
                        offending.add(file + ": name=\"" + field.group(1) + "\"");
                    }
                }
            }
        });
        assertThat(offending)
            .describedAs("a UiState parameter must not be a field of a POST form")
            .isEmpty();
    }

    /**
     * A button that opens a create form must not carry a filter parameter: the filter would be
     * rewritten by the click (ADR-0023). Where the new entry should start out with the current
     * selection, the fallback delivers it anyway; where it should start out with something else,
     * the link names the form field, not the filter.
     */
    @Test
    void noCreateLinkCarriesAFilterParameter() throws IOException {
        var offending = new ArrayList<String>();
        for (Path root : List.of(TEMPLATES, Path.of("src/main/java"))) {
            forEachFile(root, (file, content) -> {
                Matcher link = CREATE_LINK.matcher(content);
                while (link.find()) {
                    if (registeredParams.contains(link.group(1))) {
                        offending.add(file + ": /create…" + link.group(1) + "=");
                    }
                }
            }, ".html", ".java");
        }
        assertThat(offending)
            .describedAs("a link to a create form must not set a UiState filter parameter")
            .isEmpty();
    }

    @Test
    void noRegisteredParameterIsAFieldOfAFormClass() {
        var classes = new ClassFileImporter()
            .withImportOption(new DoNotIncludeTests())
            .withImportOption(new DoNotIncludeJars())
            .importPackages("org.tb");

        var offending = classes.stream()
            .filter(c -> c.getSimpleName().endsWith("Form"))
            .flatMap(c -> c.getFields().stream())
            .filter(f -> registeredParams.contains(f.getName()))
            .map(f -> f.getOwner().getSimpleName() + "." + f.getName())
            .toList();

        assertThat(offending)
            .describedAs("a UiState parameter must not be a field of a *Form class")
            .isEmpty();
    }

    private interface TemplateVisitor {
        void visit(Path file, String content) throws IOException;
    }

    private static void forEachTemplate(TemplateVisitor visitor) throws IOException {
        forEachFile(TEMPLATES, visitor, ".html");
    }

    private static void forEachFile(Path root, TemplateVisitor visitor, String... suffixes)
        throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files
                .filter(p -> Stream.of(suffixes).anyMatch(s -> p.toString().endsWith(s)))
                .toList()) {
                visitor.visit(root.relativize(file), Files.readString(file, UTF_8));
            }
        }
    }

    /**
     * The real contributors, found the way Spring finds them — a module added tomorrow is covered
     * without touching this test.
     */
    private static UiStateKeyRegistry registry() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(UiStateKeyContributor.class));
        var contributors = scanner.findCandidateComponents("org.tb").stream()
            .map(definition -> instantiate(definition.getBeanClassName()))
            .toList();
        assertThat(contributors).describedAs("UiStateKeyContributor implementations").isNotEmpty();
        return new UiStateKeyRegistry(contributors);
    }

    private static UiStateKeyContributor instantiate(String className) {
        try {
            return (UiStateKeyContributor) Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot instantiate " + className, e);
        }
    }
}
