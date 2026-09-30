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
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Die Vertragsauswahl im Kopf von {@code layout/base.html} für eine Person mit mehreren Verträgen
 * (#1229).
 *
 * <p>Nur dann erscheint neben der Auswahl das Badge mit dem Gültigkeitszeitraum. Es fragte eine
 * Eigenschaft ab, die #950 als unbenutzt entfernt hatte, weil die Suche im Java-Code die Vorlage
 * nicht sieht — und weil die Fehlerseite dasselbe Layout nutzt, fiel jede Seite im Buchungsbereich
 * aus. Gerendert wird deshalb die echte Vorlage mit dem echten View-Helper.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class EmployeeContractSelectorHeaderTest {

  private static final LocalDate TODAY = DateUtils.today();

  @Test
  void an_ended_selected_contract_gets_the_yellow_badge() {
    var employee = employee(1L);
    var ended = contract(10L, employee, TODAY.minusYears(2), TODAY.minusDays(1));
    var running = contract(11L, employee, TODAY, null);

    var html = renderHeader(List.of(ended, running), ended);

    assertThat(html).contains("bg-warning-lt").doesNotContain("bg-secondary-lt");
  }

  @Test
  void a_running_selected_contract_gets_the_grey_badge() {
    var employee = employee(1L);
    var ended = contract(10L, employee, TODAY.minusYears(2), TODAY.minusDays(1));
    var running = contract(11L, employee, TODAY, null);

    var html = renderHeader(List.of(ended, running), running);

    assertThat(html).contains("bg-secondary-lt").doesNotContain("bg-warning-lt");
  }

  private static String renderHeader(List<Employeecontract> contracts, Employeecontract selected) {
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

    var context = new Context(Locale.GERMANY);
    context.setVariable("section", "dailyreport");
    context.setVariable("subSection", "dashboard");
    context.setVariable(ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
        new ThymeleafEvaluationContext(applicationContext, new DefaultConversionService()));

    var html = engine.process("layout/base", Set.of("div.d-md-flex"), context);
    // Ohne den Zweig für mehrere Verträge prüfte der Test nichts: das Badge trägt den Zeitraum.
    assertThat(html).contains(selected.getTimeString());
    return html;
  }

  private static Employee employee(long id) {
    var employee = new Employee();
    ReflectionTestUtils.setField(employee, "id", id);
    employee.setFirstname("Erika");
    employee.setLastname("Muster");
    employee.setSign("em");
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
