package org.tb.reporting.service;

import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.util.DateTimeUtils;
import org.tb.reporting.domain.JobExecutionResult;
import org.tb.reporting.domain.ReportParameter;
import org.tb.reporting.domain.ScheduledReportExecutionHistory;
import org.tb.reporting.domain.ScheduledReportJob;
import org.tb.reporting.event.ReportScheduledEvent;
import org.tb.reporting.event.ReportUnscheduledEvent;
import org.tb.reporting.persistence.ScheduledReportExecutionHistoryRepository;
import org.tb.reporting.persistence.ScheduledReportJobRepository;

@Service
@RequiredArgsConstructor
@Transactional
@Authorized(requiresPeopleLead = true)
@Slf4j
public class ScheduledReportJobService {

  private final ScheduledReportJobRepository scheduledReportJobRepository;
  private final ReportEmailService reportEmailService;
  private final ScheduledReportExecutionHistoryRepository historyRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ApplicationEventPublisher applicationEventPublisher;
  private final AuthorizedUser authorizedUser;

  public List<ScheduledReportJob> getAllJobs() {
    if (authorizedUser.isManager()) {
      return (List<ScheduledReportJob>) scheduledReportJobRepository.findAll();
    }
    return scheduledReportJobRepository.findByCreatedby(authorizedUser.getEffectiveLoginSign());
  }

  public ScheduledReportJob getJob(Long id) {
    ScheduledReportJob job = scheduledReportJobRepository.findById(id).orElseThrow();
    checkOwnership(job);
    return job;
  }

  public ScheduledReportJob createJob(ScheduledReportJob job) {
    ScheduledReportJob saved = scheduledReportJobRepository.save(job);
    applicationEventPublisher.publishEvent(new ReportScheduledEvent(this, saved));
    return saved;
  }

  public ScheduledReportJob updateJob(ScheduledReportJob job) {
    checkOwnership(job);
    ScheduledReportJob saved = scheduledReportJobRepository.save(job);
    applicationEventPublisher.publishEvent(new ReportScheduledEvent(this, saved));
    return saved;
  }

  public void deleteJob(Long id) {
    ScheduledReportJob job = scheduledReportJobRepository.findById(id).orElseThrow();
    checkOwnership(job);
    scheduledReportJobRepository.deleteById(id);
    applicationEventPublisher.publishEvent(new ReportUnscheduledEvent(this, job));
  }

  private void checkOwnership(ScheduledReportJob job) {
    if (!authorizedUser.isManager() &&
        !authorizedUser.getEffectiveLoginSign().equals(job.getCreatedby())) {
      throw new AuthorizationException(ErrorCode.AA_NOT_ATHORIZED);
    }
  }

  /**
   * Execute a single scheduled report job by its id.
   * Returns the execution result, or empty if the job was not found or is disabled.
   */
  public Optional<JobExecutionResult> executeScheduledReportJobById(Long jobId) {
    var jobOpt = scheduledReportJobRepository.findById(jobId);
    if (jobOpt.isEmpty()) return Optional.empty();
    var job = jobOpt.get();
    if (!job.isEnabled()) {
      log.info("Skipping disabled scheduled report job ID: {}", jobId);
      return Optional.empty();
    }
    return Optional.of(executeJob(job));
  }

  @Authorized(permitAll = true)
  public String getHumanReadableCron(String cronExpr) {
    try {
      CronDefinition def = CronDefinitionBuilder.instanceDefinitionFor(CronType.SPRING53);
      CronParser parser = new CronParser(def);
      Cron cron = parser.parse(cronExpr);
      cron.validate();
      CronDescriptor descriptor = CronDescriptor.instance(Locale.ENGLISH);
      return descriptor.describe(cron);
    } catch (Exception e) {
      return "unrecognized/invalid cron pattern";
    }
  }

  private JobExecutionResult executeJob(ScheduledReportJob job) {
    log.info("Executing scheduled report job: {} (ID: {})", job.getName(), job.getId());

    LocalDateTime executedAt = DateTimeUtils.now();
    List<ReportParameter> parameters = parseParameters(job.getReportParameters());
    String[] recipients = parseRecipients(job.getRecipientEmails());

    if (recipients.length == 0) {
      String msg = "No recipients configured";
      log.warn(msg + " for job: {} (ID: {})", job.getName(), job.getId());
      historyRepository.save(historyEntry(job, executedAt, false, msg));
      throw new RuntimeException(msg);
    }

    try {
      var sendResult = reportEmailService.sendReportEmail(
          job.getReportDefinition().getId(),
          parameters,
          recipients,
          job.isSuppressEmptyResults()
      );

      if(!sendResult.error()) {
        log.info("Successfully executed scheduled report job: {} (ID: {})", job.getName(), job.getId());
        String historyMsg = sendResult.suppressed() ? "Email suppressed (empty result)" : "Email sent to " + recipients.length + " recipient(s)";
        historyRepository.save(historyEntry(job, executedAt, true, historyMsg));
        return new JobExecutionResult(job.getName(), sendResult.rowCount(), job.getRecipientEmails(), sendResult.suppressed(), false,null);
      } else {
        var errorInfo = sendResult.errorInfo();
        String message = "%s:%s".formatted(errorInfo.getErrorClass(), errorInfo.getErrorMessage());
        log.error("Error executing job: {} (ID: {}) {}", job.getName(), job.getId(), message);
        historyRepository.save(historyEntry(job, executedAt, false, message));
        return new JobExecutionResult(job.getName(), sendResult.rowCount(), job.getRecipientEmails(), sendResult.suppressed(), true, errorInfo);
      }

    } catch (Exception e) {
      log.error("Error executing job: {} (ID: {})", job.getName(), job.getId(), e);
      historyRepository.save(historyEntry(job, executedAt, false, safeMessage(e)));
      throw new RuntimeException("Failed to execute scheduled report job", e);
    }
  }

  private ScheduledReportExecutionHistory historyEntry(ScheduledReportJob job, LocalDateTime executedAt, boolean success, String message) {
    return ScheduledReportExecutionHistory.builder()
        .jobId(job.getId())
        .jobName(job.getName())
        .reportDefinitionId(job.getReportDefinition() != null ? job.getReportDefinition().getId() : null)
        .reportDefinitionName(job.getReportDefinition() != null ? job.getReportDefinition().getName() : null)
        .executedAt(executedAt)
        .success(success)
        .message(message)
        .build();
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
