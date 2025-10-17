package org.tb.reporting.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.reporting.service.ReportingService;

@Component
@RequiredArgsConstructor
public class CreateScheduledReportJobAction extends LoginRequiredAction<ScheduledReportJobForm> {

  private final ReportingService reportingService;

  @Override
  protected ActionForward executeAuthenticated(ActionMapping mapping, ScheduledReportJobForm form,
      HttpServletRequest request, HttpServletResponse response) {

    request.getSession().setAttribute("reportDefinitions", reportingService.getReportDefinitions());
    request.getSession().setAttribute("scheduledReportJobForm", form);

    return mapping.findForward("success");
  }

}
