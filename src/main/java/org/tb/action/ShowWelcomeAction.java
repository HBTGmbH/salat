package org.tb.action;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Warning;
import org.tb.helper.AfterLogin;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.OvertimeDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.StatusReportDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.action.dailyreport.DailyReportAction;
import org.tb.form.ShowWelcomeForm;

@Component
public class ShowWelcomeAction extends DailyReportAction<ShowWelcomeForm> {

    private OvertimeDAO overtimeDAO;
    private TimereportDAO timereportDAO;
    private EmployeecontractDAO employeecontractDAO;
    private EmployeeorderDAO employeeorderDAO;
    private PublicholidayDAO publicholidayDAO;
    private CustomerorderDAO customerorderDAO;
    private StatusReportDAO statusReportDAO;
    private AfterLogin afterLogin;

    public ShowWelcomeAction(AfterLogin afterLogin) {
        super(afterLogin);
    }

    public void setAfterLogin(AfterLogin afterLogin) {
        this.afterLogin = afterLogin;
    }

    public void setStatusReportDAO(StatusReportDAO statusReportDAO) {
        this.statusReportDAO = statusReportDAO;
    }

    public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
        this.customerorderDAO = customerorderDAO;
    }

    public void setPublicholidayDAO(PublicholidayDAO publicholidayDAO) {
        this.publicholidayDAO = publicholidayDAO;
    }

    public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
        this.employeeorderDAO = employeeorderDAO;
    }

    public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
        this.employeecontractDAO = employeecontractDAO;
    }

    public void setTimereportDAO(TimereportDAO timereportDAO) {
        this.timereportDAO = timereportDAO;
    }

    public void setOvertimeDAO(OvertimeDAO overtimeDAO) {
        this.overtimeDAO = overtimeDAO;
    }

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
