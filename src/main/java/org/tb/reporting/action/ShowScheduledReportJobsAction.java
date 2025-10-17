package org.tb.reporting.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.reporting.service.ScheduledReportJobService;

@Component
@RequiredArgsConstructor
public class ShowScheduledReportJobsAction extends LoginRequiredAction<ActionForm> {

  private final ScheduledReportJobService scheduledReportJobService;

  @Override
  protected ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form,
      HttpServletRequest request, HttpServletResponse response) {

    var jobs = scheduledReportJobService.getAllJobs();
    request.getSession().setAttribute("scheduledReportJobs", jobs);

    return mapping.findForward("success");
  }

}
