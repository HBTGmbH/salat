package org.tb.welcome.action;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.AfterLogin;
import org.tb.common.Warning;
import org.tb.dailyreport.DailyReportAction;
import org.tb.employee.Employee;
import org.tb.employee.Employeecontract;
import org.tb.employee.EmployeecontractDAO;

@Component
@RequiredArgsConstructor
public class ShowWelcomeAction extends DailyReportAction<ShowWelcomeForm> {

    private final EmployeecontractDAO employeecontractDAO;
    private final AfterLogin afterLogin;

    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping,
        ShowWelcomeForm welcomeForm, HttpServletRequest request,
        HttpServletResponse response) throws Exception {

        Employeecontract employeecontract;

        // create collection of employeecontracts
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        List<Employeecontract> employeecontracts = employeecontractDAO.getVisibleEmployeeContractsForEmployee(loginEmployee);

        request.getSession().setAttribute("employeecontracts", employeecontracts);

        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("refresh")) {

            long employeeContractId = welcomeForm.getEmployeeContractId();

            employeecontract = employeecontractDAO.getEmployeeContractById(employeeContractId);
            request.getSession().setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
            request.getSession().setAttribute("currentEmployeeContract", employeecontract);
        } else {
            employeecontract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
            if (employeecontract == null) {
                employeecontract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
            }
            welcomeForm.setEmployeeContractId(employeecontract.getId());
            request.getSession().setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
            request.getSession().setAttribute("currentEmployeeContract", employeecontract);
        }

        refreshVacationAndOvertime(request, employeecontract);

        // warnings
        Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
        List<Warning> warnings = afterLogin.createWarnings(employeecontract, loginEmployeeContract, getResources(request), getLocale(request));

        if (!warnings.isEmpty()) {
            request.getSession().setAttribute("warnings", warnings);
            request.getSession().setAttribute("warningsPresent", true);
        } else {
            request.getSession().setAttribute("warningsPresent", false);
        }

        return mapping.findForward("success");
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }

}
