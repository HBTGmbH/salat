package org.tb.employee.action;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.service.OvertimeService;

@Component
@RequiredArgsConstructor
public class ShowOvertimeAction extends LoginRequiredAction<ShowOvertimeForm> {

  private final EmployeecontractDAO employeecontractDAO;
  private final OvertimeService overtimeService;

  @Override
  protected ActionForward executeAuthenticated(ActionMapping mapping, ShowOvertimeForm form, HttpServletRequest request,
      HttpServletResponse response) throws Exception {

    if ("refresh".equalsIgnoreCase(request.getParameter("task"))) {
      var currentEmployeeContract = employeecontractDAO.getEmployeeContractById(form.getEmployeecontractId());
      if (currentEmployeeContract != null) {
        request.getSession().setAttribute("currentEmployeeId", currentEmployeeContract.getEmployee().getId());
        request.getSession().setAttribute("currentEmployeeContract", currentEmployeeContract);
      } else {
        request.getSession().removeAttribute("currentEmployeeId");
        request.getSession().removeAttribute("currentEmployeeContract");
      }
    } else if("correct-overtime".equalsIgnoreCase(request.getParameter("task"))) {
      overtimeService.updateOvertimeStatic(form.getEmployeecontractId());
    } else {
      // init employeecontractId if not already set
      if(form.getEmployeecontractId() == -1) {
        var currentEmployeeContract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
        if(currentEmployeeContract != null) {
          form.setEmployeecontractId(currentEmployeeContract.getId());
        }
      }
    }

    // get valid employeecontracts
    List<Employeecontract> employeeContracts = employeecontractDAO.getVisibleEmployeeContractsForAuthorizedUser();
    request.setAttribute("employeecontracts", employeeContracts);

    var report = overtimeService.createDetailedReportForEmployee(form.getEmployeecontractId());
    request.setAttribute("overtimereport", report);

    return mapping.findForward("success");
  }

}
