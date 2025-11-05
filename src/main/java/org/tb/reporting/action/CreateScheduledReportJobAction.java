package org.tb.reporting.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;

/**
 * Legacy Struts action that redirects to the new Spring MVC controller.
 * @deprecated Use {@link org.tb.reporting.web.ReportingJobsController} instead.
 */
@Deprecated
@Component
@RequiredArgsConstructor
public class CreateScheduledReportJobAction extends LoginRequiredAction<ScheduledReportJobForm> {

  @Override
  protected ActionForward executeAuthenticated(ActionMapping mapping, ScheduledReportJobForm form,
      HttpServletRequest request, HttpServletResponse response) {
    // Redirect to new Spring MVC endpoint
    return new ActionForward("/reporting/jobs/create", true);
  }

}
