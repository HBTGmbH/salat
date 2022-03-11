package org.tb.chicoree;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.service.TimereportService;
import org.tb.employee.service.OvertimeService;

@Component
@RequiredArgsConstructor
public class DeleteTimereportAction extends LoginRequiredAction<ActionForm> {

  private final ChicoreeSessionStore chicoreeSessionStore;
  private final TimereportDAO timereportDAO;
  private final TimereportService timereportService;
  private final OvertimeService overtimeService;

  @Override
  protected ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request,
      HttpServletResponse response) throws Exception {
    try {
      String id = request.getParameter("id");
      if(id != null && !id.isBlank()) {
        timereportService.deleteTimereport(Long.valueOf(id), authorizedUser);
        // refresh the displayed data in the dashboard
        var date = chicoreeSessionStore.getDashboardDate().orElseThrow();
        var contractId = chicoreeSessionStore.getLoginEmployeecontractId().orElseThrow();
        var timereports = timereportDAO.getTimereportsByDateAndEmployeeContractId(contractId, date);
        chicoreeSessionStore.setTimereports(date, timereports);
        var overtimeStatus = overtimeService.calculateOvertime(contractId, true);
        chicoreeSessionStore.setOvertimeStatus(overtimeStatus);
      }
      return mapping.findForward("success");
    } catch (AuthorizationException | BusinessRuleException | InvalidDataException e) {
      addToMessages(request, e.getErrorCode());
      return mapping.getInputForward();
    }
  }

}
