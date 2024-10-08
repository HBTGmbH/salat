package org.tb.dailyreport.action;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.AuthorizedUser;
import org.tb.common.GlobalConstants;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.InvalidDataException;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.Workingday.WorkingDayType;
import org.tb.dailyreport.service.TimereportService;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.dailyreport.viewhelper.TimereportHelper;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.persistence.CustomerorderDAO;
import org.tb.order.persistence.EmployeeorderDAO;
import org.tb.order.persistence.SuborderDAO;

/**
 * action class for updating a timereport directly from daily display
 *
 * @author oda
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateDailyReportAction extends DailyReportAction<UpdateDailyReportForm> {

    private final SuborderDAO suborderDAO;
    private final CustomerorderDAO customerorderDAO;
    private final TimereportDAO timereportDAO;
    private final WorkingdayDAO workingdayDAO;
    private final EmployeeorderDAO employeeorderDAO;
    private final EmployeecontractDAO employeecontractDAO;
    private final TimereportHelper timereportHelper;
    private final TimereportService timereportService;
    private final AuthorizedUser authorizedUser;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, UpdateDailyReportForm reportForm, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (request.getParameter("trId") != null) {
            long trId = Long.parseLong(request.getParameter("trId"));
            TimereportDTO tr = timereportDAO.getTimereportById(trId);

            try {
                timereportService.updateTimereport(
                    authorizedUser,
                    trId,
                    tr.getEmployeecontractId(),
                    tr.getEmployeeorderId(),
                    tr.getReferenceday(),
                    reportForm.getComment(),
                    Boolean.TRUE.equals(reportForm.getTraining()),
                    reportForm.getSelectedDurationHour(),
                    reportForm.getSelectedDurationMinute()
                );
            } catch (AuthorizationException | BusinessRuleException | InvalidDataException e) {
                addToErrors(request, e.getErrorCode());
                return mapping.getInputForward();
            }

            // get updated list of timereports from DB
            ShowDailyReportForm showDailyReportForm = new ShowDailyReportForm();
            showDailyReportForm.setDay((String) request.getSession().getAttribute("currentDay"));
            showDailyReportForm.setMonth((String) request.getSession().getAttribute("currentMonth"));
            showDailyReportForm.setYear((String) request.getSession().getAttribute("currentYear"));
            showDailyReportForm.setLastday((String) request.getSession().getAttribute("lastDay"));
            showDailyReportForm.setLastmonth((String) request.getSession().getAttribute("lastMonth"));
            showDailyReportForm.setLastyear((String) request.getSession().getAttribute("lastYear"));
            showDailyReportForm.setEmployeeContractId(tr.getEmployeecontractId());
            showDailyReportForm.setView((String) request.getSession().getAttribute("view"));
            showDailyReportForm.setOrder((String) request.getSession().getAttribute("currentOrder"));
            showDailyReportForm.setStartdate((String) request.getSession().getAttribute("startdate"));
            showDailyReportForm.setEnddate((String) request.getSession().getAttribute("enddate"));

            Long currentSuborderId = (Long) request.getSession().getAttribute("currentSuborderId");
            if (currentSuborderId == null || currentSuborderId == 0) {
                currentSuborderId = -1L;
            }
            showDailyReportForm.setSuborderId(currentSuborderId);

            refreshTimereports(
                    request,
                    showDailyReportForm,
                    customerorderDAO,
                    timereportDAO,
                    employeecontractDAO,
                    suborderDAO,
                    employeeorderDAO
            );
            @SuppressWarnings("unchecked")
            List<TimereportDTO> timereports = (List<TimereportDTO>) request.getSession().getAttribute("timereports");

            request.getSession().setAttribute("labortime", timereportHelper.calculateLaborTime(timereports));
            request.getSession().setAttribute("maxlabortime", timereportHelper.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));

            Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(tr.getReferenceday(), tr.getEmployeecontractId());

            // save values from the data base into form-bean, when working day != null
            if (workingday != null) {

                //show break time, quitting time and working day ends on the showdailyreport.jsp
                request.getSession().setAttribute("visibleworkingday", workingday.getType() != WorkingDayType.NOT_WORKED);

                showDailyReportForm.setSelectedWorkHourBegin(workingday.getStarttimehour());
                showDailyReportForm.setSelectedWorkMinuteBegin(workingday.getStarttimeminute());
                showDailyReportForm.setSelectedBreakHour(workingday.getBreakhours());
                showDailyReportForm.setSelectedBreakMinute(workingday.getBreakminutes());
                showDailyReportForm.setWorkingDayTypeTyped(workingday.getType());
            } else {

                //show break time, quitting time and working day ends on the showdailyreport.jsp
                request.getSession().setAttribute("visibleworkingday", false);

                showDailyReportForm.setSelectedWorkHourBegin(0);
                showDailyReportForm.setSelectedWorkMinuteBegin(0);
                showDailyReportForm.setSelectedBreakHour(0);
                showDailyReportForm.setSelectedBreakMinute(0);
                showDailyReportForm.setWorkingDayTypeTyped(WorkingDayType.WORKED);
            }

            request.getSession().setAttribute("quittingtime", timereportHelper.calculateQuittingTime(workingday, request, "quittingtime"));

            //refresh overtime
            Employeecontract ec = employeecontractDAO.getEmployeeContractById(tr.getEmployeecontractId());
            refreshEmployeeSummaryData(request, ec);

            return mapping.findForward("success");
        }

        return mapping.findForward("error");
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }
}
