package org.tb.reporting.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.SimpleThreadScope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.beans.factory.config.Scope;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.reporting.domain.ScheduledReportJob;
import org.tb.reporting.persistence.ScheduledReportJobRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportSchedulerService {

  private final ScheduledReportJobRepository scheduledReportJobRepository;
  private final ReportEmailService reportEmailService;
  private final ConfigurableListableBeanFactory beanFactory;
  private final ObjectProvider<AuthorizedUser> authorizedUserProvider;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Executes all enabled scheduled report jobs nightly at 2:00 AM.
   */
  @Scheduled(cron = "${salat.reporting.scheduler.cron:0 0 2 * * ?}")
  public void executeScheduledReports() {
    runInTemporarySessionScope(() -> {
      log.info("Starting scheduled report execution");

      var jobs = scheduledReportJobRepository.findByEnabledTrue();
      log.info("Found {} enabled scheduled report jobs", jobs.size());

      for (ScheduledReportJob job : jobs) {
        try {
          executeJob(job);
        } catch (Exception e) {
          log.error("Failed to execute scheduled report job: {} (ID: {})", job.getName(), job.getId(), e);
        }
      }

      log.info("Finished scheduled report execution");
    });
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
    systemUser.setAuthenticated(true);
    systemUser.setLoginSign("SYSTEM");
    systemUser.setSign("SYSTEM");
    systemUser.setAdmin(false);
    systemUser.setManager(true);
    systemUser.setBackoffice(true);
    systemUser.setRestricted(false);
  }

  private void executeJob(ScheduledReportJob job) {
    log.info("Executing scheduled report job: {} (ID: {})", job.getName(), job.getId());

    try {
      Map<String, Object> parameters = parseParameters(job.getReportParameters());
      String[] recipients = parseRecipients(job.getRecipientEmails());

      if (recipients.length == 0) {
        log.warn("No recipients configured for job: {} (ID: {})", job.getName(), job.getId());
        return;
      }

      reportEmailService.sendReportEmail(
          job.getReportDefinition().getId(),
          parameters,
          recipients
      );

      log.info("Successfully executed scheduled report job: {} (ID: {})", job.getName(), job.getId());

    } catch (Exception e) {
      log.error("Error executing job: {} (ID: {})", job.getName(), job.getId(), e);
      throw new RuntimeException("Failed to execute scheduled report job", e);
    }
  }

  private Map<String, Object> parseParameters(String parametersJson) {
    if (parametersJson == null || parametersJson.trim().isEmpty()) {
      return new HashMap<>();
    }

    try {
      return objectMapper.readValue(parametersJson, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      log.error("Failed to parse report parameters: {}", parametersJson, e);
      return new HashMap<>();
    }
  }

  private String[] parseRecipients(String recipientEmails) {
    if (recipientEmails == null || recipientEmails.trim().isEmpty()) {
      return new String[0];
    }

    return recipientEmails.split("[,;\\s]+");
  }

}
