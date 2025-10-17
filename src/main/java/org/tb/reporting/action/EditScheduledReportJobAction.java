package org.tb.reporting.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.reporting.service.ReportingService;
import org.tb.reporting.service.ScheduledReportJobService;

@Component
@RequiredArgsConstructor
public class EditScheduledReportJobAction extends LoginRequiredAction<ScheduledReportJobForm> {

  private final ScheduledReportJobService scheduledReportJobService;
  private final ReportingService reportingService;

  @Override
  protected ActionForward executeAuthenticated(ActionMapping mapping, ScheduledReportJobForm form,
      HttpServletRequest request, HttpServletResponse response) {

    var job = scheduledReportJobService.getJob(form.getId());
    
    form.setReportDefinitionId(job.getReportDefinition().getId());
    form.setName(job.getName());
    form.setReportParameters(job.getReportParameters());
    form.setRecipientEmails(job.getRecipientEmails());
    form.setEnabled(job.isEnabled());
    form.setCronExpression(job.getCronExpression());
    form.setDescription(job.getDescription());

    request.getSession().setAttribute("reportDefinitions", reportingService.getReportDefinitions());
    request.getSession().setAttribute("scheduledReportJobForm", form);

    return mapping.findForward("success");
  }

}
