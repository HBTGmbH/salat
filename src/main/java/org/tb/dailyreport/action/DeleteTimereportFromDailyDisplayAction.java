package org.tb.dailyreport.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.GlobalConstants;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.service.TimereportService;
import org.tb.dailyreport.service.WorkingdayService;
import org.tb.dailyreport.viewhelper.TimereportHelper;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.EmployeeorderService;
import org.tb.order.service.SuborderService;

/**
 * Action class for deletion of a timereport initiated from the daily display
 *
 * @author oda
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteTimereportFromDailyDisplayAction extends DailyReportAction<ShowDailyReportForm> {

    private final EmployeecontractService employeecontractService;
    private final EmployeeorderService employeeorderService;
    private final TimereportHelper timereportHelper;
    private final TimereportService timereportService;
    private final WorkingdayService workingdayService;
    private final CustomerorderService customerorderService;
    private final SuborderService suborderService;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ShowDailyReportForm form, HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (GenericValidator.isBlankOrNull(request.getParameter("trId")) || !GenericValidator.isLong(request.getParameter("trId"))) {
            return mapping.getInputForward();
        }

        long trId = Long.parseLong(request.getParameter("trId"));
        TimereportDTO tr = timereportService.getTimereportById(trId);
        if (tr == null) {
            return mapping.getInputForward();
        }

        timereportService.deleteTimereportById(trId);

        if (!refreshTimereports(request, form, customerorderService, timereportService, employeecontractService,
                suborderService, employeeorderService)) {
            return mapping.findForward("error");
        } else {

            @SuppressWarnings("unchecked")
            List<TimereportDTO> timereports = (List<TimereportDTO>) request.getSession().getAttribute("timereports");
            request.getSession().setAttribute("labortime", timereportHelper.calculateLaborTime(timereports));
            request.getSession().setAttribute("maxlabortime", timereportHelper.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
            //refresh workingday
            Workingday workingday;
            try {
                workingday = refreshWorkingday(form, request, workingdayService);
            } catch (Exception e) {
                return mapping.findForward("error");
            }
            Employeecontract employeecontract = employeecontractService.getEmployeecontractById(form.getEmployeeContractId());
            request.getSession().setAttribute("quittingtime", timereportHelper.calculateQuittingTime(workingday, request));
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
