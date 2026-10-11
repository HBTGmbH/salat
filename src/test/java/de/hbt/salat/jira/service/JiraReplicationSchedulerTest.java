package de.hbt.salat.jira.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.util.ReflectionUtils.findField;
import static org.springframework.util.ReflectionUtils.makeAccessible;
import static org.springframework.util.ReflectionUtils.setField;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Trigger.MANUAL;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Trigger.SCHEDULED;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import org.springframework.web.client.RestClientException;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.jira.configuration.JiraSwitch;
import de.hbt.salat.jira.domain.JiraReplicationConfig;

/**
 * The hourly round over all enabled replications, each with its row in the run history (#1282).
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraReplicationSchedulerTest {

  @Mock
  private JiraReplicationService replicationService;

  @Mock
  private JiraReplicationRunService runService;

  @Mock
  private ObjectProvider<AuthorizedUser> authorizedUserProvider;

  @Mock
  private AuthorizedUser authorizedUser;

  /** The switch of the environment (#1458), on as in the defaults. */
  private final SalatProperties properties = new SalatProperties();

  @Spy
  private JiraSwitch jiraSwitch = new JiraSwitch(properties);

  @InjectMocks
  private JiraReplicationScheduler scheduler;

  @BeforeEach
  void setUp() {
    // lenient: with JIRA switched off the round never signs in
    lenient().when(authorizedUserProvider.getObject()).thenReturn(authorizedUser);
  }

  @Test
  void switched_off_the_hourly_round_drops_out_without_a_row() {
    // #1458: no run, no skipped row, not even the list of replications is read
    properties.getJira().setEnabled(false);

    scheduler.runScheduled();

    verifyNoInteractions(replicationService, runService, authorizedUserProvider);
  }

  @Test
  void switched_off_a_round_started_by_hand_is_turned_away_with_its_code() {
    // the REST interface starts the round this way; a failure inside it would only reach the log
    properties.getJira().setEnabled(false);

    assertThatThrownBy(() -> scheduler.runAll(MANUAL))
        .isInstanceOf(BusinessRuleException.class)
        .extracting(ex -> ((ErrorCodeException) ex).getMessages().getFirst().getErrorCode())
        .isEqualTo(ErrorCode.JI_DISABLED);
    verifyNoInteractions(replicationService, runService);
  }

  @Test
  void without_a_schedule_of_its_own_the_round_starts_at_a_quarter_past_every_hour() {
    assertThat(cronTasksWith(Map.of())).containsExactly("0 15 * * * *");
  }

  @Test
  void a_dash_as_schedule_switches_the_hourly_round_off() {
    // Scheduled.CRON_DISABLED: Spring does not even register the task
    assertThat(cronTasksWith(Map.of("salat.jira.replication.cron", "-"))).isEmpty();
  }

  @Test
  void the_defaults_in_application_yaml_keep_things_as_they_were() {
    var yaml = new YamlPropertiesFactoryBean();
    yaml.setResources(new ClassPathResource("application.yaml"));
    var defaults = yaml.getObject();

    assertThat(String.valueOf(defaults.get("salat.jira.enabled"))).isEqualTo("true");
    assertThat(String.valueOf(defaults.get("salat.jira.replication.cron"))).isEqualTo("0 15 * * * *");
    assertThat(new SalatProperties().getJira().isEnabled()).isTrue();
  }

  /** The expressions of the tasks Spring registers for the round. */
  @SuppressWarnings("unchecked")
  private static List<String> cronTasksWith(Map<String, Object> properties) {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
      context.registerBean(JiraReplicationScheduler.class, () -> new JiraReplicationScheduler(
          mock(JiraReplicationService.class), mock(JiraReplicationRunService.class), mock(ObjectProvider.class),
          new JiraSwitch(new SalatProperties())));
      context.registerBean(ScheduledAnnotationBeanPostProcessor.class);
      context.refresh();
      return context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks().stream()
          .map(task -> ((CronTask) task.getTask()).getExpression())
          .toList();
    }
  }

  @Test
  void a_replication_that_is_still_running_is_recorded_as_skipped() {
    when(replicationService.getEnabledReplications()).thenReturn(List.of(config(1L, "Alpha")));
    when(replicationService.runRecorded(1L, SCHEDULED))
        .thenThrow(new BusinessRuleException(ErrorCode.JI_REPLICATION_RUN_ALREADY_RUNNING, "01.10.2026 10:15:00"));

    scheduler.runScheduled();

    verify(runService).recordSkippedRun(eq(1L), eq(SCHEDULED), anyString());
  }

  /** A run started by hand of another replication of the same scope holds this one back (#1386). */
  @Test
  void a_replication_whose_scope_is_busy_is_recorded_as_skipped() {
    when(replicationService.getEnabledReplications()).thenReturn(List.of(config(1L, "Alpha")));
    when(replicationService.runRecorded(1L, SCHEDULED))
        .thenThrow(new BusinessRuleException(ErrorCode.JI_REPLICATION_RUN_SCOPE_BUSY, "Beta", "01.10.2026 10:15:00"));

    scheduler.runScheduled();

    verify(runService).recordSkippedRun(eq(1L), eq(SCHEDULED), contains("desselben Bereichs"));
  }

  @Test
  void a_failing_replication_does_not_hold_up_the_ones_behind_it() {
    // before the run history the first failure ended the whole round
    when(replicationService.getEnabledReplications()).thenReturn(List.of(config(1L, "Alpha"), config(2L, "Beta")));
    when(replicationService.runRecorded(1L, SCHEDULED)).thenThrow(new RestClientException("connection reset"));

    scheduler.runScheduled();

    verify(replicationService).runRecorded(2L, SCHEDULED);
    verify(runService, never()).recordSkippedRun(eq(1L), eq(SCHEDULED), anyString());
  }

  @Test
  void the_round_runs_in_job_mode() {
    when(replicationService.getEnabledReplications()).thenReturn(List.of());

    scheduler.runScheduled();

    verify(authorizedUser).initForJob();
  }

  private static JiraReplicationConfig config(long id, String name) {
    var config = new JiraReplicationConfig();
    var idField = findField(AuditedEntity.class, "id");
    makeAccessible(idField);
    setField(idField, config, id);
    config.setName(name);
    return config;
  }

}
