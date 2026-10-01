package de.hbt.salat.dailyreport.viewhelper;

import static de.hbt.salat.dailyreport.controller.DailyReportUiStateKeyContributor.EMPLOYEE_CONTRACT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.common.web.UiState;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeecontractService;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Der Vertragsbalken über der Kopfzeile von {@code layout/base.html} (#1231): er erscheint im
 * Bereich Buchungen für eine Person mit mehreren Verträgen und bietet deren übrige Verträge als
 * Wechsel an.
 *
 * <p>Gerendert wird die echte Vorlage mit dem echten View-Helper. Seine Vorgängerin, das Badge
 * neben der Auswahl, fragte eine Eigenschaft ab, die #950 als unbenutzt entfernt hatte, weil die
 * Suche im Java-Code die Vorlage nicht sieht — und weil die Fehlerseite dasselbe Layout nutzt, fiel
 * jede Seite im Buchungsbereich aus (#1229).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class EmployeeContractSelectorHeaderTest {

  private static final LocalDate TODAY = DateUtils.today();

  @Test
  void the_bar_names_the_selected_contract_and_offers_the_other_one() {
    var employee = employee(1L, "em");
    var ended = contract(10L, employee, LocalDate.of(2019, 1, 1), LocalDate.of(2022, 12, 31));
    var running = contract(11L, employee, LocalDate.of(2023, 1, 1), null);

    var html = renderBar(List.of(ended, running), ended, "dashboard");

    assertThat(html).contains("Erika Muster");
    assertThat(html).contains("Vertrag 01.01.2019 – 31.12.2022");
    assertThat(html).contains("Angezeigt werden nur Daten dieses Vertrags.");
    assertThat(html).contains("data-select-contract=\"11\"");
    assertThat(html).contains("ab 01.01.2023, unbefristet");
    assertThat(html).doesNotContain("data-select-contract=\"10\"");
  }

  /** Das offene Ende ist eine eigene Nachricht, kein leerer zweiter Platzhalter. */
  @Test
  void an_open_ended_contract_is_named_as_such() {
    var employee = employee(1L, "em");
    var ended = contract(10L, employee, LocalDate.of(2019, 1, 1), LocalDate.of(2022, 12, 31));
    var running = contract(11L, employee, LocalDate.of(2023, 1, 1), null);

    var html = renderBar(List.of(ended, running), running, "dashboard");

    assertThat(html).contains("Vertrag ab 01.01.2023, unbefristet");
    assertThat(html).contains("data-select-contract=\"10\"");
    assertThat(html).contains("01.01.2019 – 31.12.2022");
  }

  /** Mehrere Verträge im Selektor heißt nicht mehrere Verträge der gewählten Person. */
  @Test
  void a_person_with_one_contract_gets_no_bar() {
    var erika = contract(10L, employee(1L, "em"), TODAY.minusYears(2), null);
    var max = contract(20L, employee(2L, "mm"), TODAY.minusYears(1), null);

    assertThat(renderBar(List.of(erika, max), erika, "dashboard")).doesNotContain("contract-bar");
  }

  /** Die Abnahme wählt ihre Personen selbst; dort wirkt die Auswahl nicht. */
  @Test
  void the_acceptance_gets_no_bar() {
    var employee = employee(1L, "em");
    var ended = contract(10L, employee, TODAY.minusYears(2), TODAY.minusDays(1));
    var running = contract(11L, employee, TODAY, null);

    assertThat(renderBar(List.of(ended, running), running, "acceptance")).doesNotContain("contract-bar");
  }

  private static String renderBar(List<Employeecontract> contracts, Employeecontract selected, String subSection) {
    var employeecontractService = mock(EmployeecontractService.class);
    when(employeecontractService.getVisibleEmployeeContractsForAuthorizedUser()).thenReturn(contracts);
    var uiState = mock(UiState.class);
    when(uiState.getLongValue(EMPLOYEE_CONTRACT_ID)).thenReturn(selected.getId());
    var viewHelper = new EmployeeContractSelectorViewHelper(employeecontractService, uiState,
        mock(AuthorizedEmployee.class));

    var applicationContext = new StaticApplicationContext();
    applicationContext.getBeanFactory().registerSingleton("employeeContractSelectorViewHelper", viewHelper);
    applicationContext.refresh();

    var resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setCharacterEncoding("UTF-8");
    var engine = new SpringTemplateEngine();
    engine.setTemplateResolver(resolver);
    var messages = new ResourceBundleMessageSource();
    messages.setBasename("de/hbt/salat/web/MessageResources");
    messages.setDefaultEncoding("UTF-8");
    // Deutsch ist das Bundle ohne Suffix. Mit Rückfall auf die Systemsprache fände Locale.GERMANY
    // auf einem englischen Rechner — dem CI-Runner — zuerst MessageResources_en.
    messages.setFallbackToSystemLocale(false);
    engine.setTemplateEngineMessageSource(messages);

    var context = new Context(Locale.GERMANY);
    context.setVariable("section", "dailyreport");
    context.setVariable("subSection", subSection);
    context.setVariable(ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
        new ThymeleafEvaluationContext(applicationContext, new DefaultConversionService()));

    return engine.process("layout/base", Set.of("div.contract-bar"), context);
  }

  private static Employee employee(long id, String sign) {
    var employee = new Employee();
    ReflectionTestUtils.setField(employee, "id", id);
    employee.setFirstname("Erika");
    employee.setLastname("Muster");
    employee.setSign(sign);
    return employee;
  }

  private static Employeecontract contract(long id, Employee employee, LocalDate from, LocalDate until) {
    var contract = new Employeecontract();
    ReflectionTestUtils.setField(contract, "id", id);
    contract.setEmployee(employee);
    contract.setValidFrom(from);
    contract.setValidUntil(until);
    return contract;
  }
}
