package org.tb.reporting.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.reporting.domain.ScheduledReportJob;
import org.tb.reporting.persistence.ScheduledReportJobRepository;

@Service
@RequiredArgsConstructor
@Transactional
@Authorized(requiresManager = true)
public class ScheduledReportJobService {

  private final ScheduledReportJobRepository scheduledReportJobRepository;
  private final ScheduledReportJobScheduler scheduledReportJobScheduler;

  public List<ScheduledReportJob> getAllJobs() {
    return (List<ScheduledReportJob>) scheduledReportJobRepository.findAll();
  }

  public ScheduledReportJob getJob(Long id) {
    return scheduledReportJobRepository.findById(id).orElseThrow();
  }

  public ScheduledReportJob createJob(ScheduledReportJob job) {
    ScheduledReportJob saved = scheduledReportJobRepository.save(job);
    scheduledReportJobScheduler.scheduleOrUnschedule(saved);
    return saved;
  }

  public ScheduledReportJob updateJob(ScheduledReportJob job) {
    ScheduledReportJob saved = scheduledReportJobRepository.save(job);
    scheduledReportJobScheduler.scheduleOrUnschedule(saved);
    return saved;
  }

  public void deleteJob(Long id) {
    scheduledReportJobScheduler.unscheduleJob(id);
    scheduledReportJobRepository.deleteById(id);
  }

}
