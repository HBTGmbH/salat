package org.tb.action.dailyreport;

import java.io.IOException;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessages;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.GlobalConstants;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Timereport;
import org.tb.bdom.Workingday;
import org.tb.helper.AfterLogin;
import org.tb.helper.TimereportHelper;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.WorkingdayDAO;
import org.tb.form.ShowDailyReportForm;

/**
 * Action class for deletion of a timereport initiated from the daily display
 *
 * @author oda
 */
@Component("/DeleteTimereportFromDailyDisplay")
@Slf4j
public class DeleteTimereportFromDailyDisplayAction extends DailyReportAction<ShowDailyReportForm> {

    private final CustomerorderDAO customerorderDAO;
    private final TimereportDAO timereportDAO;
    private final EmployeecontractDAO employeecontractDAO;
    private final SuborderDAO suborderDAO;
    private final EmployeeorderDAO employeeorderDAO;
    private final WorkingdayDAO workingdayDAO;
    private final TimereportHelper timereportHelper;

    @Autowired
    public DeleteTimereportFromDailyDisplayAction(AfterLogin afterLogin,
        CustomerorderDAO customerorderDAO, TimereportDAO timereportDAO,
        EmployeecontractDAO employeecontractDAO, SuborderDAO suborderDAO,
        EmployeeorderDAO employeeorderDAO, WorkingdayDAO workingdayDAO, TimereportHelper timereportHelper) {
        super(afterLogin);
        this.customerorderDAO = customerorderDAO;
        this.timereportDAO = timereportDAO;
        this.employeecontractDAO = employeecontractDAO;
        this.suborderDAO = suborderDAO;
        this.employeeorderDAO = employeeorderDAO;
        this.workingdayDAO = workingdayDAO;
        this.timereportHelper = timereportHelper;
    }

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ShowDailyReportForm form, HttpServletRequest request, HttpServletResponse response) throws IOException {

        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }

        if (GenericValidator.isBlankOrNull(request.getParameter("trId")) || !GenericValidator.isLong(request.getParameter("trId"))) {
            return mapping.getInputForward();
        }

        long trId = Long.parseLong(request.getParameter("trId"));
        Timereport tr = timereportDAO.getTimereportById(trId);
        if (tr == null) {
            return mapping.getInputForward();
        }

        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");

        if (!timereportDAO.deleteTimereportById(trId)) {
            return mapping.findForward("error");
        }

        if (!refreshTimereports(request, form, customerorderDAO, timereportDAO, employeecontractDAO,
                suborderDAO, employeeorderDAO)) {
            return mapping.findForward("error");
        } else {

            @SuppressWarnings("unchecked")
            List<Timereport> timereports = (List<Timereport>) request.getSession().getAttribute("timereports");
            request.getSession().setAttribute("labortime", timereportHelper.calculateLaborTime(timereports));
            request.getSession().setAttribute("maxlabortime", timereportHelper.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
            request.getSession().setAttribute("dailycosts", timereportHelper.calculateDailyCosts(timereports));
            //refresh workingday
            Workingday workingday;
            try {
                workingday = refreshWorkingday(form, request, workingdayDAO);
            } catch (Exception e) {
                return mapping.findForward("error");
            }
            Employeecontract employeecontract = employeecontractDAO.getEmployeeContractById(form.getEmployeeContractId());
            request.getSession().setAttribute("quittingtime", timereportHelper.calculateQuittingTime(workingday, request, "quittingtime"));
            if (employeecontract != null) {
                request.getSession().setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
                request.getSession().setAttribute("currentEmployeeContract", employeecontract);
            }
            return mapping.findForward("success");
        }

    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }
}
