package org.tb.common;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Guards the German and English message bundles against drift. A key that only exists in the
 * German bundle silently falls back to German text in the English UI (#823), and a key that no
 * bundle defines renders as {@code ??key??} for everyone.
 */
class MessageResourcesConsistencyTest {

  private static final String GERMAN_BUNDLE = "/org/tb/web/MessageResources.properties";
  private static final String ENGLISH_BUNDLE = "/org/tb/web/MessageResources_en.properties";

  /**
   * Matches the key of a {@code #{...}} message expression, both plain ({@code #{some.key}}) and
   * parameterised ({@code #{some.key(${arg})}}). Expressions whose key is assembled at runtime
   * (e.g. {@code #{${'main.employee.status.' + status}}}) deliberately do not match - their keys
   * cannot be checked statically.
   */
  private static final Pattern MESSAGE_EXPRESSION = Pattern.compile("#\\{\\s*([A-Za-z0-9_.\\-]+)\\s*[(}]");

  /** Argument index of a {@link java.text.MessageFormat} placeholder, e.g. the 0 in {@code {0}}. */
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)");

  @Test
  void bothBundlesDefineTheSameKeys() throws IOException {
    Set<String> german = keysOf(GERMAN_BUNDLE);
    Set<String> english = keysOf(ENGLISH_BUNDLE);

    assertThat(missing(german, english))
        .as("keys missing in MessageResources_en.properties - the English UI would show German text")
        .isEmpty();
    assertThat(missing(english, german))
        .as("keys missing in MessageResources.properties - the German UI would show English text")
        .isEmpty();
  }

  @Test
  void everyMessageKeyUsedInATemplateIsDefined() throws IOException {
    Set<String> german = keysOf(GERMAN_BUNDLE);
    Set<String> english = keysOf(ENGLISH_BUNDLE);
    var undefined = new TreeMap<String, Set<String>>();

    for (Resource template : new PathMatchingResourcePatternResolver()
        .getResources("classpath*:/templates/**/*.html")) {
      String content = new String(template.getInputStream().readAllBytes(), UTF_8);
      Matcher matcher = MESSAGE_EXPRESSION.matcher(content);
      while (matcher.find()) {
        String key = matcher.group(1);
        if (!german.contains(key) || !english.contains(key)) {
          undefined.computeIfAbsent(key, k -> new TreeSet<>()).add(template.getFilename());
        }
      }
    }

    assertThat(undefined)
        .as("message keys used in a template but not defined in both bundles")
        .isEmpty();
  }

  /**
   * A translation that drops a {@code {0}} placeholder silently swallows the argument the caller
   * passes in, so both bundles have to use the same set of placeholders per key.
   */
  @Test
  void bothBundlesUseTheSamePlaceholdersPerKey() throws IOException {
    Properties german = load(GERMAN_BUNDLE);
    Properties english = load(ENGLISH_BUNDLE);
    var mismatches = new TreeMap<String, String>();

    for (String key : new TreeSet<>(german.stringPropertyNames())) {
      Set<String> inGerman = placeholders(german.getProperty(key));
      Set<String> inEnglish = placeholders(english.getProperty(key, ""));
      if (!inGerman.equals(inEnglish)) {
        mismatches.put(key, "de=" + inGerman + " en=" + inEnglish);
      }
    }

    assertThat(mismatches).as("keys whose translations use different message arguments").isEmpty();
  }

  @Test
  void noBundleContainsAnEmptyValue() throws IOException {
    for (String bundle : Arrays.asList(GERMAN_BUNDLE, ENGLISH_BUNDLE)) {
      Properties properties = load(bundle);
      Set<String> blank = new TreeSet<>();
      properties.stringPropertyNames().stream()
          .filter(key -> properties.getProperty(key).isBlank())
          .forEach(blank::add);
      assertThat(blank).as("keys without a translation in %s", bundle).isEmpty();
    }
  }

  private static Set<String> placeholders(String message) {
    Set<String> indices = new TreeSet<>();
    Matcher matcher = PLACEHOLDER.matcher(message);
    while (matcher.find()) {
      indices.add(matcher.group(1));
    }
    return indices;
  }

  private static Set<String> missing(Set<String> expected, Set<String> actual) {
    Set<String> missing = new TreeSet<>(expected);
    missing.removeAll(actual);
    return missing;
  }

  private static Set<String> keysOf(String bundle) throws IOException {
    return new TreeSet<>(load(bundle).stringPropertyNames());
  }

  private static Properties load(String bundle) throws IOException {
    Properties properties = new Properties();
    try (Reader reader = new InputStreamReader(
        MessageResourcesConsistencyTest.class.getResourceAsStream(bundle), UTF_8)) {
      properties.load(reader);
    }
    return properties;
  }

}
