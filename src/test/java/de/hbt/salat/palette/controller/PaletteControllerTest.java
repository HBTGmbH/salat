package de.hbt.salat.palette.controller;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static de.hbt.salat.common.palette.PaletteKind.CUSTOMER;
import static de.hbt.salat.common.palette.PaletteKind.CUSTOMERORDER;
import static de.hbt.salat.common.palette.PaletteKind.PERSON;
import static de.hbt.salat.common.palette.PaletteKind.SUBORDER;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import de.hbt.salat.common.palette.PaletteCommand;
import de.hbt.salat.common.palette.PaletteHit;
import de.hbt.salat.common.palette.PaletteKind;
import de.hbt.salat.common.palette.PaletteParameter;
import de.hbt.salat.common.palette.PaletteSuggestion;
import de.hbt.salat.common.palette.PaletteTarget;
import de.hbt.salat.common.palette.PaletteText;
import de.hbt.salat.palette.service.PaletteGroup;
import de.hbt.salat.palette.service.PaletteSearchService;

/**
 * The endpoint of the palette's object search (#1157): what the service found, as the fragment that salat.js takes
 * apart into the rows of the palette.
 *
 * <p>The providers hand out message keys with their arguments, never texts, and salat.js relies on the attributes of
 * the fragment and on the order of the targets — the first one is what Enter opens. None of that shows before the
 * template runs, so the fragment is rendered for real, with the message bundle: a key that does not resolve comes out
 * as {@code ??key??}, not as an error. Who finds what is decided by the providers and tested there; the controller
 * passes on what the service answers.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class PaletteControllerTest {

  private static final Pattern RESULTS = Pattern.compile("<div[^>]*\\sdata-palette-results[\\s>=]");
  private static final Pattern GROUP = Pattern.compile(
      "<section(?=[^>]*\\sdata-palette-group[\\s>=])[^>]*>.*?</section>", Pattern.DOTALL);
  private static final Pattern HIT = Pattern.compile(
      "<article(?=[^>]*\\sdata-palette-hit[\\s>=])[^>]*>.*?</article>", Pattern.DOTALL);
  private static final Pattern PART = Pattern.compile("<(h3|span|a)\\b([^>]*)>(.*?)</\\1>", Pattern.DOTALL);

  private static final PaletteHit ORDER = new PaletteHit(CUSTOMERORDER, "MUSTER-01", "MUSTER-01",
      "Wartungsvertrag · MUSTER", null, false, false, 4, List.of(
          target("main.palette.target.customerorder.open", "/orders/customerorders/edit?id=11", PaletteTarget.OPEN),
          target("main.palette.target.customerorder.suborders",
              "/orders/suborders?fCustomerOrderId=11&fCustomerId=&fSuborderFilter=", 1),
          target("main.palette.target.customerorder.controlling",
              "/budget/controlling?fBudgetCustomerOrderId=11&evaluate=true", 2),
          target("main.palette.target.customerorder.budget",
              "/budget?fBudgetCustomerOrderId=11&fBudgetShowInactive=false", 3)));

  private static final PaletteHit ENDED_ORDER = new PaletteHit(CUSTOMERORDER, "MUSTER-02", "MUSTER-02", null, null,
      true, false, 4, List.of(
          target("main.palette.target.customerorder.open", "/orders/customerorders/edit?id=12", PaletteTarget.OPEN)));

  private static final PaletteHit SUBORDER_HIT = new PaletteHit(SUBORDER, "31", "MUSTER-01/03", "Wartung",
      PaletteText.of("main.palette.context.plain", "MUSTER"), false, false, 4, List.of(
          target("main.palette.target.suborder.open", "/orders/suborders/31/edit", PaletteTarget.OPEN),
          new PaletteTarget(PaletteText.of("main.palette.target.suborder.book", "MUSTER-01/03"),
              "/dailyreport/timereports/new?suborderId=31&employeecontractId=7", 5)));

  private static final PaletteHit CUSTOMER_HIT = new PaletteHit(CUSTOMER, "5", "MUSTER", "Musterkunde", null,
      false, true, 4, List.of(
          target("main.palette.target.customer.open", "/customers/edit?id=5", PaletteTarget.OPEN),
          target("main.palette.target.customer.orders",
              "/orders/customerorders?fCustomerId=5&fCustomerOrderFilter=", 1)));

  private static final PaletteHit PERSON_HIT = new PaletteHit(PERSON, "7", "Person P", "ppp",
      PaletteText.of("main.palette.context.contract.since", "01.01.2024"), false, false, 3, List.of(
          target("main.palette.target.person.employee", "/employees/edit?id=3", PaletteTarget.OPEN),
          target("main.palette.target.person.contract", "/employees/contracts/edit?id=7", 1),
          target("main.palette.target.person.daily", "/dailyreport/daily?fEmployeeContractId=7", 10),
          target("main.palette.target.person.matrix", "/dailyreport/matrix?fEmployeeContractId=7", 11)));

  @Mock
  private PaletteSearchService paletteSearchService;

  private PaletteController controller;

  @BeforeEach
  void setUp() {
    controller = new PaletteController(paletteSearchService);
  }

  @Test
  void a_search_answers_with_the_results_fragment_and_the_groups_of_the_service() throws Exception {
    var groups = List.of(new PaletteGroup(CUSTOMERORDER, List.of(ORDER)));
    when(paletteSearchService.search("muster")).thenReturn(groups);

    MockMvcBuilders.standaloneSetup(controller).build()
        .perform(get("/palette/search").param("q", "muster"))
        .andExpect(status().isOk())
        .andExpect(view().name("palette/search-results :: results"))
        .andExpect(model().attribute("groups", groups));
  }

  /**
   * salat.js asks from two characters on, but the endpoint does not rely on that: without {@code q} the service gets
   * the empty text and decides itself that there is nothing to search for.
   */
  @Test
  void without_a_query_the_service_gets_an_empty_text_and_the_fragment_stays_empty() throws Exception {
    var html = renderFragment(get("/palette/search"));

    verify(paletteSearchService).search("");
    assertThat(html).containsPattern(RESULTS).doesNotContain("<section", "<article");
  }

  /** The answer is merged into a palette that is already open, so it is the fragment alone, not a page. */
  @Test
  void every_group_stands_under_its_heading_in_the_order_of_the_service() throws Exception {
    var html = render(List.of(
        new PaletteGroup(CUSTOMERORDER, List.of(ORDER, ENDED_ORDER)),
        new PaletteGroup(SUBORDER, List.of(SUBORDER_HIT)),
        new PaletteGroup(CUSTOMER, List.of(CUSTOMER_HIT)),
        new PaletteGroup(PERSON, List.of(PERSON_HIT))));

    assertThat(html).containsPattern(RESULTS).doesNotContain("<html", "<body");
    assertThat(groups(html)).containsExactly(
        "CUSTOMERORDER Aufträge: MUSTER-01, MUSTER-02",
        "SUBORDER Unteraufträge: 31",
        "CUSTOMER Auftraggeber: 5",
        "PERSON Personen: 7");
  }

  @Test
  void a_hit_shows_title_subtitle_and_its_context_with_the_arguments() throws Exception {
    var html = render(List.of(
        new PaletteGroup(SUBORDER, List.of(SUBORDER_HIT)),
        new PaletteGroup(PERSON, List.of(PERSON_HIT))));

    var person = hit(html, PERSON, "7");
    assertThat(texts(person, "title")).containsExactly("Person P");
    assertThat(texts(person, "subtitle")).containsExactly("ppp");
    assertThat(texts(person, "context")).containsExactly("Vertrag seit 01.01.2024");
    var suborder = hit(html, SUBORDER, "31");
    assertThat(texts(suborder, "title")).containsExactly("MUSTER-01/03");
    assertThat(texts(suborder, "subtitle")).containsExactly("Wartung");
    assertThat(texts(suborder, "context")).containsExactly("MUSTER");
  }

  @Test
  void a_hit_without_subtitle_and_context_leaves_both_out() throws Exception {
    var html = render(List.of(new PaletteGroup(CUSTOMERORDER, List.of(ENDED_ORDER))));

    var order = hit(html, CUSTOMERORDER, "MUSTER-02");
    assertThat(texts(order, "title")).containsExactly("MUSTER-02");
    assertThat(texts(order, "subtitle")).isEmpty();
    assertThat(texts(order, "context")).isEmpty();
  }

  /** Ended and hidden objects are still found, ranked lower by the service and marked here (ADR-0012, ADR-0029). */
  @Test
  void ended_and_hidden_hits_are_marked_and_current_ones_are_not() throws Exception {
    var endedAndHidden = new PaletteHit(CUSTOMERORDER, "MUSTER-03", "MUSTER-03", null, null, true, true, 4, List.of(
        target("main.palette.target.customerorder.open", "/orders/customerorders/edit?id=13", PaletteTarget.OPEN)));
    var html = render(List.of(
        new PaletteGroup(CUSTOMERORDER, List.of(ORDER, ENDED_ORDER, endedAndHidden)),
        new PaletteGroup(CUSTOMER, List.of(CUSTOMER_HIT))));

    assertThat(texts(hit(html, CUSTOMERORDER, "MUSTER-01"), "marker")).isEmpty();
    assertThat(texts(hit(html, CUSTOMERORDER, "MUSTER-02"), "marker")).containsExactly("beendet");
    assertThat(texts(hit(html, CUSTOMERORDER, "MUSTER-03"), "marker")).containsExactly("beendet", "verborgen");
    assertThat(texts(hit(html, CUSTOMER, "5"), "marker")).containsExactly("verborgen");
  }

  /**
   * The targets stand in the order the service ranked them, and their labels are resolved with their arguments:
   * "Buchen auf" names the suborder it books on.
   */
  @Test
  void the_targets_are_links_in_rank_order_with_their_labels_resolved() throws Exception {
    var html = render(List.of(
        new PaletteGroup(CUSTOMERORDER, List.of(ORDER)),
        new PaletteGroup(SUBORDER, List.of(SUBORDER_HIT)),
        new PaletteGroup(CUSTOMER, List.of(CUSTOMER_HIT)),
        new PaletteGroup(PERSON, List.of(PERSON_HIT))));

    assertThat(targets(hit(html, CUSTOMERORDER, "MUSTER-01"))).containsExactly(
        "Auftrag öffnen -> /orders/customerorders/edit?id=11",
        "Unteraufträge -> /orders/suborders?fCustomerOrderId=11&fCustomerId=&fSuborderFilter=",
        "Controlling, ausgewertet -> /budget/controlling?fBudgetCustomerOrderId=11&evaluate=true",
        "Budget -> /budget?fBudgetCustomerOrderId=11&fBudgetShowInactive=false");
    assertThat(targets(hit(html, SUBORDER, "31"))).containsExactly(
        "Unterauftrag öffnen -> /orders/suborders/31/edit",
        "Buchen auf MUSTER-01/03 -> /dailyreport/timereports/new?suborderId=31&employeecontractId=7");
    assertThat(targets(hit(html, CUSTOMER, "5"))).containsExactly(
        "Auftraggeber öffnen -> /customers/edit?id=5",
        "Aufträge des Auftraggebers -> /orders/customerorders?fCustomerId=5&fCustomerOrderFilter=");
    assertThat(targets(hit(html, PERSON, "7"))).containsExactly(
        "Mitarbeiter -> /employees/edit?id=3",
        "Vertrag -> /employees/contracts/edit?id=7",
        "Einzelübersicht -> /dailyreport/daily?fEmployeeContractId=7",
        "Matrixübersicht -> /dailyreport/matrix?fEmployeeContractId=7");
  }

  /**
   * Titles and descriptions come from the database, and salat.js builds the palette's rows from the fragment: markup
   * in a description has to arrive as text. The parameters of a link are joined with ampersands, escaped as well.
   */
  @Test
  void texts_and_links_from_the_hits_are_escaped() throws Exception {
    var order = new PaletteHit(CUSTOMERORDER, "MUSTER-04", "MUSTER-04", "Wartung & <b>Pflege</b>", null, false,
        false, 4, List.of(target("main.palette.target.customerorder.open",
            "/orders/customerorders?fCustomerOrderFilter=MUSTER-04&fCustomerId=5", PaletteTarget.OPEN)));

    var html = render(List.of(new PaletteGroup(CUSTOMERORDER, List.of(order))));

    assertThat(html)
        .contains("Wartung &amp; &lt;b&gt;Pflege&lt;/b&gt;")
        .doesNotContain("<b>")
        .contains("href=\"/orders/customerorders?fCustomerOrderFilter=MUSTER-04&amp;fCustomerId=5\"");
  }

  // --- the parameters of the commands (#1158) -----------------------------------------------------

  @Test
  void a_suggestion_request_hands_command_parameter_text_day_and_contract_to_the_service() throws Exception {
    var suggestions = List.of(PaletteSuggestion.of("2026-09", "2026-09", null, null));
    when(paletteSearchService.suggest(PaletteCommand.ACCEPT, PaletteParameter.MONTH, "sep", LocalDate.of(2026, 9, 25), 7L))
        .thenReturn(suggestions);

    MockMvcBuilders.standaloneSetup(controller).build()
        .perform(get("/palette/suggest").param("command", "ACCEPT").param("parameter", "MONTH").param("q", "sep")
            .param("date", "2026-09-25").param("contractId", "7"))
        .andExpect(status().isOk())
        .andExpect(view().name("palette/suggestions :: suggestions"))
        .andExpect(model().attribute("suggestions", suggestions));
  }

  @Test
  void without_command_or_parameter_there_are_no_suggestions_and_the_service_is_not_asked() throws Exception {
    var html = renderFragment(get("/palette/suggest").param("parameter", "MONTH"));

    verifyNoInteractions(paletteSearchService);
    assertThat(html).contains("data-palette-suggestions").doesNotContain("<article");
  }

  /** The value, the flags salat.js reads, and every text with its arguments resolved and escaped. */
  @Test
  void a_suggestion_carries_its_value_its_flags_and_its_texts() throws Exception {
    when(paletteSearchService.suggest(PaletteCommand.BOOK, PaletteParameter.SUBORDER, "wart", null, null)).thenReturn(List.of(
        new PaletteSuggestion("501", "MUSTER-01.03", "Wartung & <b>Pflege</b>",
            PaletteText.of("main.palette.suggestion.notbookable", "01.08.2026"), true, true, true),
        PaletteSuggestion.of("502", "MUSTER-01.04", null, null)));

    var html = renderFragment(get("/palette/suggest").param("command", "BOOK").param("parameter", "SUBORDER").param("q", "wart"));

    var articles = Pattern.compile("<article[^>]*>.*?</article>", Pattern.DOTALL).matcher(html).results()
        .map(MatchResult::group).toList();
    assertThat(articles).hasSize(2);
    assertThat(articles.get(0))
        .contains("data-value=\"501\"", "data-disabled=\"true\"", "data-comment-required=\"true\"", "data-exact=\"true\"")
        .contains("MUSTER-01.03", "Wartung &amp; &lt;b&gt;Pflege&lt;/b&gt;", "am 01.08.2026 nicht buchbar");
    assertThat(articles.get(1))
        .contains("data-value=\"502\"")
        .doesNotContain("data-disabled", "data-comment-required", "data-exact", "data-part=\"detail\"", "data-part=\"note\"");
  }

  private static PaletteTarget target(String key, String href, int rank) {
    return new PaletteTarget(PaletteText.of(key), href, rank);
  }

  /** The fragment for {@code muster}, with the groups the service answers. */
  private String render(List<PaletteGroup> groups) throws Exception {
    when(paletteSearchService.search("muster")).thenReturn(groups);
    return renderFragment(get("/palette/search").param("q", "muster"));
  }

  /** The request through Thymeleaf, so that the template itself runs, with the message bundle in German. */
  private String renderFragment(MockHttpServletRequestBuilder request) throws Exception {
    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("de/hbt/salat/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);

    var templateResolver = new ClassLoaderTemplateResolver();
    templateResolver.setPrefix("templates/");
    templateResolver.setSuffix(".html");
    templateResolver.setCharacterEncoding("UTF-8");
    var templateEngine = new SpringTemplateEngine();
    templateEngine.setTemplateResolver(templateResolver);
    templateEngine.setTemplateEngineMessageSource(messageSource);

    var viewResolver = new ThymeleafViewResolver();
    viewResolver.setTemplateEngine(templateEngine);
    viewResolver.setCharacterEncoding("UTF-8");

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setViewResolvers(viewResolver)
        .setLocaleResolver(new FixedLocaleResolver(Locale.GERMANY))
        .build();
    return mockMvc.perform(request).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString(UTF_8);
  }

  /** The groups as {@code KIND heading: key, key}, in the order of the fragment. */
  private static List<String> groups(String html) {
    return GROUP.matcher(html).results().map(MatchResult::group)
        .map(group -> attribute(openingTag(group), "data-kind") + " " + texts(group, "heading").getFirst() + ": "
            + HIT.matcher(group).results().map(MatchResult::group)
                .map(hit -> attribute(openingTag(hit), "data-key"))
                .collect(Collectors.joining(", ")))
        .toList();
  }

  /** The markup of the hit with this kind and key, as salat.js finds it by {@code data-kind} and {@code data-key}. */
  private static String hit(String html, PaletteKind kind, String key) {
    return HIT.matcher(html).results().map(MatchResult::group)
        .filter(hit -> kind.name().equals(attribute(openingTag(hit), "data-kind"))
            && key.equals(attribute(openingTag(hit), "data-key")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no hit " + kind + "/" + key));
  }

  /** The texts of the parts of this name, in order. */
  private static List<String> texts(String markup, String part) {
    return parts(markup, part).map(match -> match.group(3).strip()).toList();
  }

  /** The targets as {@code label -> href}, in order; the href as the browser reads it, entities resolved. */
  private static List<String> targets(String hit) {
    return parts(hit, "target")
        .map(match -> match.group(3).strip() + " -> " + attribute(match.group(2), "href").replace("&amp;", "&"))
        .toList();
  }

  private static Stream<MatchResult> parts(String markup, String part) {
    return PART.matcher(markup).results()
        .filter(match -> match.group(2).contains("data-part=\"" + part + "\""));
  }

  private static String openingTag(String element) {
    return element.substring(0, element.indexOf('>'));
  }

  private static String attribute(String attributes, String name) {
    var match = Pattern.compile("\\s" + Pattern.quote(name) + "=\"([^\"]*)\"").matcher(attributes);
    return match.find() ? match.group(1) : null;
  }
}
