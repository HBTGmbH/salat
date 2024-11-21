package org.tb.welcome.action;

import static java.lang.Boolean.TRUE;
import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.AfterLogin;
import org.tb.auth.AuthService;
import org.tb.common.Warning;
import org.tb.dailyreport.action.DailyReportAction;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.welcome.viewhelper.WelcomeViewHelper;

@Component
@RequiredArgsConstructor
public class ShowWelcomeAction extends DailyReportAction<ShowWelcomeForm> {

    private final EmployeecontractService employeecontractService;
    private final AfterLogin afterLogin;
    private final AuthService authService;
    private final EmployeeService employeeService;

    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping,
        ShowWelcomeForm welcomeForm, HttpServletRequest request,
        HttpServletResponse response) throws Exception {

        if("switch-login".equals(request.getParameter("task"))) {
            authService.switchLogin(welcomeForm.getLoginEmployeeId());
            var loginEmployee = employeeService.getEmployeeById(authorizedUser.getEmployeeId());
            request.getSession().setAttribute("loginEmployee", loginEmployee);
            String loginEmployeeFullName = loginEmployee.getFirstname() + " " + loginEmployee.getLastname();
            request.getSession().setAttribute("loginEmployeeFullName", loginEmployeeFullName);
            request.getSession().setAttribute("currentEmployeeId", loginEmployee.getId());
        }

        // collect login contracts
        var loginEmployees = authService.getLoginEmployees();
        request.getSession().setAttribute("loginEmployees", loginEmployees);

        // create collection of employeecontracts
        List<Employeecontract> employeecontracts = employeecontractService.getViewableEmployeeContractsForAuthorizedUserValidAt(today());
        request.getSession().setAttribute("employeecontracts", employeecontracts);

        Employeecontract employeecontract;
        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("refresh")) {

            long employeeContractId = welcomeForm.getEmployeeContractId();

            employeecontract = employeecontractService.getEmployeeContractById(employeeContractId);
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

        refreshEmployeeSummaryData(request, employeecontract);

        // warnings
        Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
        List<Warning> warnings = afterLogin.createWarnings(employeecontract, loginEmployeeContract, getResources(request), getLocale(request));

        if (!warnings.isEmpty()) {
            request.getSession().setAttribute("warnings", warnings);
            request.getSession().setAttribute("warningsPresent", true);
        } else {
            request.getSession().setAttribute("warningsPresent", false);
        }

        welcomeForm.setLoginEmployeeId(authorizedUser.getEmployeeId());
        welcomeForm.setEmployeeContractId(employeecontract.getId());

        boolean displayEmployeeInfo = true;

        if(TRUE.equals(employeecontract.getFreelancer())) {
            displayEmployeeInfo = false;
        }

        var welcomeViewHelper = new WelcomeViewHelper(displayEmployeeInfo);
        request.setAttribute("welcomeViewHelper", welcomeViewHelper);

        return mapping.findForward("success");
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }

}
