package org.tb.chicoree;

import static org.tb.common.util.DateUtils.today;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.service.TimereportService;
import org.tb.employee.domain.OvertimeStatus;
import org.tb.employee.service.EmployeecontractService;
import org.tb.employee.service.OvertimeService;

@Component
@RequiredArgsConstructor
public class ShowDashboardAction extends LoginRequiredAction<ActionForm> {

  private final ChicoreeSessionStore chicoreeSessionStore;
  private final TimereportDAO timereportDAO;
  private final OvertimeService overtimeService;

  @Override
  protected ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request,
      HttpServletResponse response) {
    var today = today();
    var contractId = chicoreeSessionStore.getLoginEmployeecontractId().orElseThrow();
    var timereports = timereportDAO.getTimereportsByDateAndEmployeeContractId(contractId, today);
    chicoreeSessionStore.setTimereports(today, timereports);
    var overtimeStatus = overtimeService.calculateOvertime(contractId, true);
    chicoreeSessionStore.setOvertimeStatus(overtimeStatus);
    return mapping.findForward("success");
  }

}
