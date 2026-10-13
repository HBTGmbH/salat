package de.hbt.salat.budget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import de.hbt.salat.budget.domain.FlatRateRhythm;
import de.hbt.salat.budget.domain.OrderFlatRate;
import de.hbt.salat.budget.service.OrderFlatRateService;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.common.viewhelper.FilterHintViewHelper;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;
import de.hbt.salat.order.viewhelper.CustomerorderViewHelper;

/**
 * Die Detailseite einer Pauschale, mit dem echten Template gerendert. Eine Pauschale ist nie inaktiv
 * (#1438), nur ihr Auftrag kann es sein: Der Kopf zeigt dann dieselbe Plakette wie die Liste und sonst
 * keine. Bis zu dieser Prüfung fragte er nach einer Gültigkeit der Pauschale, die es seit #1438 nicht
 * mehr gibt, und jede Detailseite brach ab.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class OrderFlatRateControllerDetailTest {

  private static final long FLAT_RATE_ID = 7L;

  private final OrderFlatRateService orderFlatRateService = mock(OrderFlatRateService.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
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

    var controller = new OrderFlatRateController(orderFlatRateService, mock(CustomerorderService.class),
        mock(SuborderService.class), mock(CustomerorderViewHelper.class), mock(ErrorCodeViewHelper.class),
        mock(FilterHintViewHelper.class), new MessageSourceAccessor(messageSource, Locale.GERMANY));
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setViewResolvers(viewResolver)
        .setLocaleResolver(new FixedLocaleResolver(Locale.GERMANY))
        .build();
  }

  @Test
  void the_page_of_a_flat_rate_on_an_active_order_shows_no_inactive_badge() throws Exception {
    givenFlatRate(null);

    var html = render();

    assertThat(html).contains("Einrichtung").contains("4711").doesNotContain("Inaktiv");
  }

  @Test
  void the_page_of_a_flat_rate_on_an_inactive_order_shows_the_badge_of_the_list() throws Exception {
    givenFlatRate(LocalDate.now().minusDays(1));

    assertThat(render()).containsPattern("<span class=\"badge bg-secondary-lt ms-1\"\\s*>Inaktiv</span>");
  }

  private void givenFlatRate(LocalDate orderUntil) {
    var order = new Customerorder();
    order.setSign("4711");
    order.setFromDate(LocalDate.of(2026, 1, 1));
    order.setUntilDate(orderUntil);
    var flatRate = new OrderFlatRate();
    ReflectionTestUtils.setField(flatRate, "id", FLAT_RATE_ID);
    flatRate.setCustomerorder(order);
    flatRate.setDescription("Einrichtung");
    flatRate.setRhythm(FlatRateRhythm.ONCE);
    flatRate.setAmount(new BigDecimal("1200.00"));
    flatRate.setValidFrom(LocalDate.of(2026, 3, 1));
    flatRate.setValidUntil(LocalDate.of(2026, 3, 1));
    when(orderFlatRateService.getById(FLAT_RATE_ID)).thenReturn(flatRate);
  }

  private String render() throws Exception {
    var response = mockMvc.perform(get("/budget/flat-rate/{id}", FLAT_RATE_ID)).andReturn().getResponse();
    assertThat(response.getStatus()).isEqualTo(200);
    return response.getContentAsString();
  }
}
