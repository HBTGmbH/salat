package de.hbt.salat.common.web;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * No template reaches into a Java class with a SpEL type expression {@code T(...)}. The class name
 * is a string there: the compiler does not see it and a package refactoring does not find it, so a
 * rename breaks the page only when it is rendered — the move from {@code org.tb} to
 * {@code de.hbt.salat} (#1195) had to find such a reference by grep. What a template needs from
 * Java comes from the controller as a model attribute; formatting goes through {@code #temporals}
 * or {@code #numbers}.
 */
class TemplateTypeExpressionTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    private static final Pattern TYPE_EXPRESSION = Pattern.compile("\\bT\\(\\s*[A-Za-z_][\\w.]*\\s*\\)");

    @Test
    void noTemplateUsesATypeExpression() throws IOException {
        var offending = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".html")).toList()) {
                var matcher = TYPE_EXPRESSION.matcher(Files.readString(file, UTF_8));
                while (matcher.find()) {
                    offending.add(TEMPLATES.relativize(file) + ": " + matcher.group());
                }
            }
        }
        assertThat(offending)
            .describedAs("a template takes Java values from the model, not through T(...)")
            .isEmpty();
    }
}
