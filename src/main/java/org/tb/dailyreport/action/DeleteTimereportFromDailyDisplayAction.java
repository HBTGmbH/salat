package org.tb.dailyreport.action;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.GlobalConstants;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.dailyreport.service.TimereportService;
import org.tb.dailyreport.viewhelper.TimereportHelper;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.persistence.CustomerorderDAO;
import org.tb.order.persistence.EmployeeorderDAO;
import org.tb.order.persistence.SuborderDAO;

/**
 * Action class for deletion of a timereport initiated from the daily display
 *
 * @author oda
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteTimereportFromDailyDisplayAction extends DailyReportAction<ShowDailyReportForm> {

    private final CustomerorderDAO customerorderDAO;
    private final TimereportDAO timereportDAO;
    private final EmployeecontractDAO employeecontractDAO;
    private final SuborderDAO suborderDAO;
    private final EmployeeorderDAO employeeorderDAO;
    private final WorkingdayDAO workingdayDAO;
    private final TimereportHelper timereportHelper;
    private final TimereportService timereportService;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ShowDailyReportForm form, HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (GenericValidator.isBlankOrNull(request.getParameter("trId")) || !GenericValidator.isLong(request.getParameter("trId"))) {
            return mapping.getInputForward();
        }

        long trId = Long.parseLong(request.getParameter("trId"));
        TimereportDTO tr = timereportDAO.getTimereportById(trId);
        if (tr == null) {
            return mapping.getInputForward();
        }

        if (!timereportService.deleteTimereport(trId, authorizedUser)) {
            return mapping.findForward("error");
        }

        if (!refreshTimereports(request, form, customerorderDAO, timereportDAO, employeecontractDAO,
                suborderDAO, employeeorderDAO)) {
            return mapping.findForward("error");
        } else {

            @SuppressWarnings("unchecked")
            List<TimereportDTO> timereports = (List<TimereportDTO>) request.getSession().getAttribute("timereports");
            request.getSession().setAttribute("labortime", timereportHelper.calculateLaborTime(timereports));
            request.getSession().setAttribute("maxlabortime", timereportHelper.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
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
