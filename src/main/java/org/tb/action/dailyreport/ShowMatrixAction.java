package org.tb.action.dailyreport;

import java.util.Map;
import java.util.Map.Entry;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.GlobalConstants;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.helper.EmployeeHelper;
import org.tb.helper.matrix.MatrixHelper;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;

@Component
@RequiredArgsConstructor
public class ShowMatrixAction extends DailyReportAction<ShowMatrixForm> {

    private final CustomerorderDAO customerorderDAO;
    private final TimereportDAO timereportDAO;
    private final EmployeecontractDAO employeecontractDAO;
    private final SuborderDAO suborderDAO;
    private final PublicholidayDAO publicholidayDAO;
    private final EmployeeDAO employeeDAO;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ShowMatrixForm reportForm, HttpServletRequest request, HttpServletResponse response) {

        // check if special tasks initiated from the daily display need to be
        // carried out...
        String task = request.getParameter("task");

        // call on MatrixView with parameter print
        if ("print".equals(task)) {
            return mapping.findForward("print");
        }

        MatrixHelper mh = new MatrixHelper(timereportDAO, employeecontractDAO, publicholidayDAO, customerorderDAO, suborderDAO, employeeDAO);
        // call on MatrixView with parameter refreshMergedreports to update request
        if ("refreshMergedreports".equals(task)) {
            Map<String, Object> results = mh.refreshMergedReports(reportForm);
            return finishHandling(results, request, mh, mapping);
        }

        // call on MatrixView with any parameter to forward or go back
        if (task != null) {
            // just go back to main menu
            return mapping.findForward(task.equalsIgnoreCase("back") ? "backtomenu" : "success");
        } else {
            reportForm.setInvoice(true);
            reportForm.setNonInvoice(true);
            // call on MatrixView without a parameter

            // no special task - prepare everything to show reports
            Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
            EmployeeHelper eh = new EmployeeHelper();
            Employeecontract ec = eh.setCurrentEmployee(loginEmployee, request, employeeDAO, employeecontractDAO);

            Map<String, Object> results = mh.handleNoArgs(
                    reportForm,
                    ec,
                    (Employeecontract) request.getSession().getAttribute("currentEmployeeContract"),
                    (Long) request.getSession().getAttribute("currentEmployeeId"),
                    (String) request.getSession().getAttribute("currentMonth"),
                    loginEmployee);
            return finishHandling(results, request, mh, mapping);
        }
    }

    private ActionForward finishHandling(Map<String, Object> results, HttpServletRequest request, MatrixHelper mh, ActionMapping mapping) {
        String errorValue = null;
        for (Entry<String, Object> entry : results.entrySet()) {
            if (mh.isHandlingError(entry.getKey())) {
                errorValue = (String) entry.getValue();
            } else {
                request.getSession().setAttribute(entry.getKey(), entry.getValue());
            }
        }
        if (errorValue != null) {
            request.setAttribute("errorMessage", errorValue);
            return mapping.findForward("error");
        }
        request.getSession().setAttribute("overtimeCompensation", GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION);
        request.getSession().setAttribute("oTCText", GlobalConstants.OVERTIME_COMPENSATION_TEXT);
        return mapping.findForward("success");
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }

}
