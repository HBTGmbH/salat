package org.tb.dailyreport.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.service.TimereportService;
import org.tb.dailyreport.service.WorkingdayService;
import org.tb.dailyreport.viewhelper.TimereportHelper;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.EmployeeorderService;
import org.tb.order.service.SuborderService;

/**
 * action class for updating a timereport directly from daily display
 *
 * @author oda
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateDailyReportAction extends DailyReportAction<UpdateDailyReportForm> {

    private final SuborderService suborderService;
    private final CustomerorderService customerorderService;
    private final WorkingdayService workingdayService;
    private final EmployeeorderService employeeorderService;
    private final EmployeecontractService employeecontractService;
    private final TimereportHelper timereportHelper;
    private final TimereportService timereportService;
    private final AuthorizedUser authorizedUser;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, UpdateDailyReportForm reportForm, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (request.getParameter("trId") != null) {
            long trId = Long.parseLong(request.getParameter("trId"));
            TimereportDTO tr = timereportService.getTimereportById(trId);

            timereportService.updateTimereport(
                trId,
                tr.getEmployeecontractId(),
                tr.getEmployeeorderId(),
                tr.getReferenceday(),
                reportForm.getComment(),
                Boolean.TRUE.equals(reportForm.getTraining()),
                reportForm.getSelectedDurationHour(),
                reportForm.getSelectedDurationMinute()
            );

            return mapping.findForward("success");
        }

        return mapping.findForward("error");
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }
}
