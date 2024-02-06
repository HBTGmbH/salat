package org.tb.dailyreport.action;

import static org.tb.common.DateTimeViewHelper.getShortstringFromMonthMM;
import static org.tb.common.util.DateUtils.getDateFormStrings;
import static org.tb.common.util.DateUtils.today;

import java.util.Map;
import java.util.Map.Entry;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.GlobalConstants;
import org.tb.dailyreport.viewhelper.matrix.MatrixHelper;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.viewhelper.EmployeeViewHelper;

@Component
@RequiredArgsConstructor
public class ShowMatrixAction extends DailyReportAction<ShowMatrixForm> {

    private final EmployeecontractDAO employeecontractDAO;
    private final EmployeeDAO employeeDAO;
    private final MatrixHelper matrixHelper;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ShowMatrixForm reportForm, HttpServletRequest request, HttpServletResponse response) {

        // check if special tasks initiated from the daily display need to be
        // carried out...
        String task = request.getParameter("task");

        // call on MatrixView with parameter print
        if ("print".equals(task)) {
            return mapping.findForward("print");
        }

        // call on MatrixView with parameter refreshMergedreports to update request
        if ("refreshMergedreports".equals(task)) {
            Map<String, Object> results = matrixHelper.refreshMergedReports(reportForm, request);
            return finishHandling(results, request, matrixHelper, mapping);
        }

        if ("setMonth".equals(task)) {
            var mode = request.getParameter("mode");
            var date = getDateFormStrings("1", reportForm.getFromMonth(), reportForm.getFromYear(), false);
            switch(mode) {
                case "0":
                    date = today();
                    break;
                default:
                    date = date.plusMonths(Long.valueOf(mode));
            }
            String fromMonth = getShortstringFromMonthMM(date.getMonthValue());
            String fromYear = String.valueOf(date.getYear());
            reportForm.setFromMonth(fromMonth);
            reportForm.setFromYear(fromYear);
            Map<String, Object> results = matrixHelper.refreshMergedReports(reportForm, request);
            return finishHandling(results, request, matrixHelper, mapping);
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
            EmployeeViewHelper eh = new EmployeeViewHelper();
            Employeecontract ec = eh.getAndInitCurrentEmployee(request, employeeDAO, employeecontractDAO);

            Map<String, Object> results = matrixHelper.handleNoArgs(
                    reportForm,
                    ec,
                    (Employeecontract) request.getSession().getAttribute("currentEmployeeContract"),
                    (Long) request.getSession().getAttribute("currentEmployeeId"),
                    (String) request.getSession().getAttribute("currentMonth"));
            return finishHandling(results, request, matrixHelper, mapping);
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
