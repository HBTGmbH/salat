package org.tb.dailyreport.action;

import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.dailyreport.service.OvertimeService;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;

@Component
@RequiredArgsConstructor
public class ShowOvertimeAction extends LoginRequiredAction<ShowOvertimeForm> {

  private final EmployeecontractService employeecontractService;
  private final OvertimeService overtimeService;

  @Override
  protected ActionForward executeAuthenticated(ActionMapping mapping, ShowOvertimeForm form, HttpServletRequest request,
      HttpServletResponse response) throws Exception {

    if ("refresh".equalsIgnoreCase(request.getParameter("task"))) {
      var currentEmployeeContract = employeecontractService.getEmployeeContractById(form.getEmployeecontractId());
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
    List<Employeecontract> employeeContracts = employeecontractService.getViewableEmployeeContractsForAuthorizedUserValidAt(today());
    request.setAttribute("employeecontracts", employeeContracts);

    var report = overtimeService.createDetailedReportForEmployee(form.getEmployeecontractId());
    request.setAttribute("overtimereport", report);

    return mapping.findForward("success");
  }

}
