package org.tb.reporting.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.reporting.domain.ScheduledReportJob;
import org.tb.reporting.service.ReportingService;
import org.tb.reporting.service.ScheduledReportJobService;

@Component
@RequiredArgsConstructor
public class StoreScheduledReportJobAction extends LoginRequiredAction<ScheduledReportJobForm> {

  private final ScheduledReportJobService scheduledReportJobService;
  private final ReportingService reportingService;

  @Override
  protected ActionForward executeAuthenticated(ActionMapping mapping, ScheduledReportJobForm form,
      HttpServletRequest request, HttpServletResponse response) {

    ScheduledReportJob job;
    var editingExistingJob = isEditingExistingJob(form);
    if (editingExistingJob) {
      job = scheduledReportJobService.getJob(form.getId());
    } else {
      job = new ScheduledReportJob();
    }

    var reportDefinition = reportingService.getReportDefinition(form.getReportDefinitionId());
    job.setReportDefinition(reportDefinition);
    job.setName(form.getName());
    job.setReportParameters(form.getReportParameters());
    job.setRecipientEmails(form.getRecipientEmails());
    job.setEnabled(form.isEnabled());
    job.setCronExpression(form.getCronExpression());
    job.setDescription(form.getDescription());

    if (editingExistingJob) {
      scheduledReportJobService.updateJob(job);
    } else {
      scheduledReportJobService.createJob(job);
    }

    return mapping.findForward("success");
  }

  private static boolean isEditingExistingJob(ScheduledReportJobForm form) {
    return form.getId() != null && form.getId() > 0;
  }

}
