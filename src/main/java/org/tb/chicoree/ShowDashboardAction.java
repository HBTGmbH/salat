package org.tb.chicoree;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.LoginRequiredAction;
import org.tb.dailyreport.service.OvertimeService;
import org.tb.dailyreport.service.TimereportService;

@Component
@RequiredArgsConstructor
public class ShowDashboardAction extends LoginRequiredAction<ActionForm> {

  private final ChicoreeSessionStore chicoreeSessionStore;
  private final TimereportService timereportService;
  private final OvertimeService overtimeService;

  @Override
  protected ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request,
      HttpServletResponse response) {
    var dashboardDate = chicoreeSessionStore.getDashboardDate().orElseThrow();
    var contractId = chicoreeSessionStore.getLoginEmployeecontractId().orElseThrow();
    var timereports = timereportService.getTimereportsForEmployeecontractAndDate(contractId, dashboardDate);
    chicoreeSessionStore.setTimereports(dashboardDate, timereports);
    var overtimeStatus = overtimeService.calculateOvertime(contractId, true);
    chicoreeSessionStore.setOvertimeStatus(overtimeStatus);
    return mapping.findForward("success");
  }

}
