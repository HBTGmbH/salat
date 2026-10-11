package de.hbt.salat.etl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static de.hbt.salat.common.exception.ErrorCode.ETL_RUN_ALREADY_RUNNING;
import static de.hbt.salat.common.exception.ErrorCode.ETL_RUN_NOT_RUNNING;
import static de.hbt.salat.etl.domain.ETLRunHistory.Trigger.SCHEDULED;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.etl.configuration.EtlSwitch;

/**
 * Der nächtliche ETL-Lauf (#1071).
 *
 * <p>Er ist der einzige Weg in einen Lauf, hinter dem niemand sitzt. Was ein von Hand angestoßener
 * Lauf als Meldung auf der Seite beantwortet bekäme — „es läuft schon einer" —, muss hier in die
 * Liste, sonst stünde der Ausfall allein im Log und die Liste zeigte eine Lücke, die von einer
 * abgeschalteten Anwendung nicht zu unterscheiden wäre.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ETLSchedulerTest {

  @Mock
  private ETLService etlService;

  @Mock
  private ObjectProvider<AuthorizedUser> authorizedUserProvider;

  @Mock
  private AuthorizedUser authorizedUser;

  /** Der Schalter der Umgebung (#1458), wie in der Vorgabe eingeschaltet. */
  private final SalatProperties properties = new SalatProperties();

  @Spy
  private EtlSwitch etlSwitch = new EtlSwitch(properties);

  @InjectMocks
  private ETLScheduler etlScheduler;

  @Test
  void the_nightly_run_works_on_the_last_three_months_as_the_system() {
    when(authorizedUserProvider.getObject()).thenReturn(authorizedUser);

    etlScheduler.runDaily();

    // Ohne HTTP-Anfrage gibt es keinen SecurityContext; ohne initForJob käme der Lauf an der
    // Rechteprüfung der Definitionen nicht vorbei (→ ADR-0006).
    verify(authorizedUser).initForJob();
    verify(etlService).executeAll(eq(lastThreeMonths()), eq(SCHEDULED));
    verify(etlService, never()).recordSkippedRun(any(), any(), anyString());
  }

  @Test
  void a_nightly_run_that_finds_another_one_running_leaves_a_skipped_entry_instead_of_failing() {
    when(authorizedUserProvider.getObject()).thenReturn(authorizedUser);
    doThrow(new BusinessRuleException(ETL_RUN_ALREADY_RUNNING, "24.09.2026 14:03:11"))
        .when(etlService).executeAll(any(), eq(SCHEDULED));

    // Kein Weiterwerfen: der geplante Job ist niemandes Anfrage, und ein Ausfall aus einem
    // erwarteten Grund ist kein Fehler des Jobs.
    assertThatCode(() -> etlScheduler.runDaily()).doesNotThrowAnyException();

    var range = ArgumentCaptor.forClass(LocalDateRange.class);
    var message = ArgumentCaptor.forClass(String.class);
    verify(etlService).recordSkippedRun(range.capture(), eq(SCHEDULED), message.capture());
    assertThat(range.getValue()).isEqualTo(lastThreeMonths());
    assertThat(message.getValue()).contains("bereits ein Lauf");
  }

  @Test
  void any_other_business_rule_still_ends_the_nightly_run_loudly() {
    // Nur die eine Absage ist vorgesehen. Jede andere Geschäftsregel bliebe sonst unbemerkt, weil
    // der Job keinen Adressaten hat, der nachfragt.
    when(authorizedUserProvider.getObject()).thenReturn(authorizedUser);
    doThrow(new BusinessRuleException(ETL_RUN_NOT_RUNNING))
        .when(etlService).executeAll(any(), eq(SCHEDULED));

    assertThatThrownBy(() -> etlScheduler.runDaily())
        .isInstanceOf(BusinessRuleException.class);

    verify(etlService, never()).recordSkippedRun(any(), any(), anyString());
  }

  @Test
  void switched_off_the_nightly_run_drops_out_without_a_row() {
    // #1458: eine abgeschaltete Umgebung hat keinen Ausfall festzuhalten — weder ein Lauf noch eine
    // übersprungene Zeile, und auch keine Anmeldung als Job.
    properties.getEtl().setEnabled(false);

    assertThatCode(() -> etlScheduler.runDaily()).doesNotThrowAnyException();

    verifyNoInteractions(etlService, authorizedUserProvider);
  }

  @Test
  void without_a_schedule_of_its_own_the_run_starts_at_two_in_the_morning() {
    assertThat(cronTasksWith(Map.of())).containsExactly("0 0 2 * * *");
    assertThat(cronTasksWith(Map.of("salat.etl.scheduler.cron", "0 30 3 * * *"))).containsExactly("0 30 3 * * *");
  }

  @Test
  void a_dash_as_schedule_switches_the_nightly_run_off() {
    // Scheduled.CRON_DISABLED — Spring meldet die Aufgabe gar nicht erst an.
    assertThat(cronTasksWith(Map.of("salat.etl.scheduler.cron", "-"))).isEmpty();
  }

  @Test
  void the_defaults_in_application_yaml_keep_things_as_they_were() {
    var yaml = new YamlPropertiesFactoryBean();
    yaml.setResources(new ClassPathResource("application.yaml"));
    var defaults = yaml.getObject();

    assertThat(String.valueOf(defaults.get("salat.etl.enabled"))).isEqualTo("true");
    assertThat(String.valueOf(defaults.get("salat.etl.scheduler.cron"))).isEqualTo("0 0 2 * * *");
    assertThat(new SalatProperties().getEtl().isEnabled()).isTrue();
  }

  /** Die Ausdrücke der Aufgaben, die Spring für den nächtlichen Lauf anmeldet. */
  @SuppressWarnings("unchecked")
  private static List<String> cronTasksWith(Map<String, Object> properties) {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
      context.registerBean(ETLScheduler.class, () -> new ETLScheduler(mock(ETLService.class),
          mock(ObjectProvider.class), new EtlSwitch(new SalatProperties())));
      context.registerBean(ScheduledAnnotationBeanPostProcessor.class);
      context.refresh();
      return context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks().stream()
          .map(task -> ((CronTask) task.getTask()).getExpression())
          .toList();
    }
  }

  private static LocalDateRange lastThreeMonths() {
    var today = DateUtils.today();
    return new LocalDateRange(today.minusMonths(3), today);
  }

}
