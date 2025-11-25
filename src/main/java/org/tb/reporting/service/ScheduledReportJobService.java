package org.tb.reporting.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.support.SimpleThreadScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.reporting.domain.ReportParameter;
import org.tb.reporting.domain.ScheduledReportExecutionHistory;
import org.tb.reporting.domain.ScheduledReportJob;
import org.tb.reporting.event.ReportScheduledEvent;
import org.tb.reporting.persistence.ScheduledReportExecutionHistoryRepository;
import org.tb.reporting.persistence.ScheduledReportJobRepository;

@Service
@RequiredArgsConstructor
@Transactional
@Authorized(requiresManager = true)
@Slf4j
public class ScheduledReportJobService {

  private final ScheduledReportJobRepository scheduledReportJobRepository;
  private final ReportEmailService reportEmailService;
  private final ConfigurableListableBeanFactory beanFactory;
  private final ObjectProvider<AuthorizedUser> authorizedUserProvider;
  private final ScheduledReportExecutionHistoryRepository historyRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ApplicationEventPublisher applicationEventPublisher;

  public List<ScheduledReportJob> getAllJobs() {
    return (List<ScheduledReportJob>) scheduledReportJobRepository.findAll();
  }

  public ScheduledReportJob getJob(Long id) {
    return scheduledReportJobRepository.findById(id).orElseThrow();
  }

  public ScheduledReportJob createJob(ScheduledReportJob job) {
    ScheduledReportJob saved = scheduledReportJobRepository.save(job);
    applicationEventPublisher.publishEvent(new ReportScheduledEvent(this, saved));
    return saved;
  }

  public ScheduledReportJob updateJob(ScheduledReportJob job) {
    ScheduledReportJob saved = scheduledReportJobRepository.save(job);
    applicationEventPublisher.publishEvent(new ReportScheduledEvent(this, saved));
    return saved;
  }

  public void deleteJob(Long id) {
    ScheduledReportJob job = scheduledReportJobRepository.findById(id).orElseThrow();
    applicationEventPublisher.publishEvent(new ReportScheduledEvent(this, job));
    scheduledReportJobRepository.deleteById(id);
  }

  /**
   * Execute a single scheduled report job by its id within a temporary session scope.
   */
  public void executeScheduledReportJobById(Long jobId) {
    runInTemporarySessionScope(() -> {
      scheduledReportJobRepository.findById(jobId).ifPresent(job -> {
        if (job.isEnabled()) {
          try {
            executeJob(job);
          } catch (Exception e) {
            log.error("Failed to execute scheduled report job: {} (ID: {})", job.getName(), job.getId(), e);
          }
        } else {
          log.info("Skipping disabled scheduled report job ID: {}", jobId);
        }
      });
    });
  }

  private void runInTemporarySessionScope(Runnable task) {
    // Initialize a temporary session scope for the duration of this scheduled execution
    Scope previousSessionScope = beanFactory.getRegisteredScope(WebApplicationContext.SCOPE_SESSION);
    var temporarySessionScope = new SimpleThreadScope();
    beanFactory.registerScope(WebApplicationContext.SCOPE_SESSION, temporarySessionScope);

    try {
      initializeAuthorizedUserForJobExecution();
      task.run();
    } finally {
      // Clean up session-scoped beans and restore original session scope
      try {
        beanFactory.destroyScopedBean("authorizedUser");
      } catch (Exception ignored) {
        // ignore cleanup issues
      }
      if (previousSessionScope != null) {
        beanFactory.registerScope(WebApplicationContext.SCOPE_SESSION, previousSessionScope);
      }
    }
  }

  private void initializeAuthorizedUserForJobExecution() {
    // Initialize a synthetic AuthorizedUser within this session scope
    AuthorizedUser systemUser = authorizedUserProvider.getObject();
    systemUser.initForJob();
  }

  private void executeJob(ScheduledReportJob job) {
    log.info("Executing scheduled report job: {} (ID: {})", job.getName(), job.getId());

    LocalDateTime executedAt = LocalDateTime.now();
    try {
      List<ReportParameter> parameters = parseParameters(job.getReportParameters());
      String[] recipients = parseRecipients(job.getRecipientEmails());

      if (recipients.length == 0) {
        String msg = "No recipients configured";
        log.warn(msg + " for job: {} (ID: {})", job.getName(), job.getId());
        historyRepository.save(ScheduledReportExecutionHistory.builder()
            .jobId(job.getId())
            .jobName(job.getName())
            .reportDefinitionId(job.getReportDefinition() != null ? job.getReportDefinition().getId() : null)
            .reportDefinitionName(job.getReportDefinition() != null ? job.getReportDefinition().getName() : null)
            .executedAt(executedAt)
            .success(false)
            .message(msg)
            .build());
        return;
      }

      reportEmailService.sendReportEmail(
          job.getReportDefinition().getId(),
          parameters,
          recipients
      );

      log.info("Successfully executed scheduled report job: {} (ID: {})", job.getName(), job.getId());
      historyRepository.save(ScheduledReportExecutionHistory.builder()
          .jobId(job.getId())
          .jobName(job.getName())
          .reportDefinitionId(job.getReportDefinition() != null ? job.getReportDefinition().getId() : null)
          .reportDefinitionName(job.getReportDefinition() != null ? job.getReportDefinition().getName() : null)
          .executedAt(executedAt)
          .success(true)
          .message("Email sent to " + recipients.length + " recipient(s)")
          .build());

    } catch (Exception e) {
      log.error("Error executing job: {} (ID: {})", job.getName(), job.getId(), e);
      historyRepository.save(ScheduledReportExecutionHistory.builder()
          .jobId(job.getId())
          .jobName(job.getName())
          .reportDefinitionId(job.getReportDefinition() != null ? job.getReportDefinition().getId() : null)
          .reportDefinitionName(job.getReportDefinition() != null ? job.getReportDefinition().getName() : null)
          .executedAt(executedAt)
          .success(false)
          .message(safeMessage(e))
          .build());
      throw new RuntimeException("Failed to execute scheduled report job", e);
    }
  }

  private String safeMessage(Exception e) {
    String msg = e.getMessage();
    if (msg == null) {
      msg = e.getClass().getSimpleName();
    }
    // Limit to 3900 to be safe for DB column length 4000
    if (msg.length() > 3900) {
      msg = msg.substring(0, 3900);
    }
    return msg;
  }

  private List<ReportParameter> parseParameters(String parametersJson) {
    if (parametersJson == null || parametersJson.trim().isEmpty()) {
      return List.of();
    }

    try {
      return objectMapper.readValue(parametersJson, new TypeReference<List<ReportParameter>>() {});
    } catch (Exception e) {
      log.error("Failed to parse report parameters: {}", parametersJson, e);
      return List.of();
    }
  }

  private String[] parseRecipients(String recipientEmails) {
    if (recipientEmails == null || recipientEmails.trim().isEmpty()) {
      return new String[0];
    }

    return recipientEmails.split("[,;\\s]+");
  }

}
