package de.hbt.salat.common.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.linkbuilder.StandardLinkBuilder;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * Kopf und Fuss eines Ausdrucks (#1147, #1148) stehen als CSS-Zeichenketten in einem {@code @page}-Block, und die
 * Matrix schreibt dort einen Personennamen hinein. Ein Anfuehrungszeichen im Namen darf die Zeichenkette nicht beenden:
 * sonst verwirft Chrome das Randfeld ohne Meldung - oder der Rest des Namens wird als CSS gelesen.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class PrintPageFragmentTest {

  /** Ein CSS-Wert in Anfuehrungszeichen: alles bis zum naechsten unmaskierten Anfuehrungszeichen. */
  private static final Pattern CSS_STRING = Pattern.compile("content: \"((?:\\\\.|[^\"\\\\])*)\";");

  @Test
  void a_name_with_quotes_stays_one_css_string_and_reads_back_unchanged() {
    var name = "Anna \"Nana\" O'Neil \\ {x}";

    var css = render("Matrixübersicht · September 2026", name, "27.09.2026 12:05");

    assertThat(cssStrings(css)).containsExactly(
        "Matrixübersicht · September 2026", name, "Stand: 27.09.2026 12:05");
  }

  @Test
  void an_empty_detail_leaves_the_field_empty() {
    var css = render("Matrixübersicht · September 2026", "", "27.09.2026 12:05");

    assertThat(cssStrings(css)).contains("");
  }

  private static String render(String title, String detail, String asOf) {
    var fragments = new ClassLoaderTemplateResolver();
    fragments.setPrefix("templates/");
    fragments.setSuffix(".html");
    fragments.setCharacterEncoding("UTF-8");
    fragments.setCheckExistence(true);
    fragments.setOrder(1);
    var caller = new StringTemplateResolver();
    caller.setOrder(2);

    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("de/hbt/salat/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);

    var engine = new SpringTemplateEngine();
    engine.addTemplateResolver(fragments);
    engine.addTemplateResolver(caller);
    engine.setTemplateEngineMessageSource(messageSource);
    // Das Logo ist ein kontextrelativer Link; ausserhalb einer Web-Anfrage gibt es keinen Kontextpfad.
    engine.setLinkBuilder(new StandardLinkBuilder() {
      @Override
      protected String computeContextPath(IExpressionContext context, String base, Map<String, Object> parameters) {
        return "";
      }
    });

    var context = new Context(Locale.GERMANY, Map.of("title", title, "detail", detail, "asOf", asOf));
    return engine.process(
        "<style th:replace=\"~{fragments/print-page :: page(${title}, ${detail}, ${asOf})}\"></style>", context);
  }

  /** Die Zeichenketten der Randfelder, wie der Browser sie liest: Maskierungen aufgeloest, ohne die Logo-URL. */
  private static List<String> cssStrings(String css) {
    return CSS_STRING.matcher(css).results()
        .map(match -> unescape(match.group(1)))
        .toList();
  }

  private static String unescape(String value) {
    return Pattern.compile("\\\\(?:([0-9A-Fa-f]{1,6}) ?|(.))").matcher(value).replaceAll(match ->
        Matcher.quoteReplacement(match.group(1) != null
            ? Character.toString(Integer.parseInt(match.group(1), 16))
            : match.group(2)));
  }
}
