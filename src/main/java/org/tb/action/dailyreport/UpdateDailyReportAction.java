package org.tb.action.dailyreport;

import java.io.IOException;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.GlobalConstants;
import org.tb.bdom.AuthorizedUser;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Timereport;
import org.tb.bdom.Workingday;
import org.tb.exception.AuthorizationException;
import org.tb.exception.BusinessRuleException;
import org.tb.exception.InvalidDataException;
import org.tb.form.ShowDailyReportForm;
import org.tb.form.UpdateDailyReportForm;
import org.tb.helper.AfterLogin;
import org.tb.helper.TimereportHelper;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.WorkingdayDAO;
import org.tb.service.TimereportService;

/**
 * action class for updating a timereport directly from daily display
 *
 * @author oda
 */
@Component
@Slf4j
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

    @Autowired
    public UpdateDailyReportAction(AfterLogin afterLogin, SuborderDAO suborderDAO,
        CustomerorderDAO customerorderDAO, TimereportDAO timereportDAO,
        WorkingdayDAO workingdayDAO,
        EmployeeorderDAO employeeorderDAO,
        EmployeecontractDAO employeecontractDAO, TimereportHelper timereportHelper,
        TimereportService timereportService, AuthorizedUser authorizedUser) {
        super(afterLogin);
        this.suborderDAO = suborderDAO;
        this.customerorderDAO = customerorderDAO;
        this.timereportDAO = timereportDAO;
        this.workingdayDAO = workingdayDAO;
        this.employeeorderDAO = employeeorderDAO;
        this.employeecontractDAO = employeecontractDAO;
        this.timereportHelper = timereportHelper;
        this.timereportService = timereportService;
        this.authorizedUser = authorizedUser;
    }

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, UpdateDailyReportForm reportForm, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (request.getParameter("trId") != null) {
            long trId = Long.parseLong(request.getParameter("trId"));
            Timereport tr = timereportDAO.getTimereportById(trId);
            Employeecontract ec = tr.getEmployeecontract();

            tr.setTaskdescription(reportForm.getComment());
            tr.setDurationhours(reportForm.getSelectedDurationHour());
            tr.setDurationminutes(reportForm.getSelectedDurationMinute());
            tr.setCosts(reportForm.getCosts());
            tr.setTraining(reportForm.getTraining());

            try {
                timereportService.updateTimereport(
                    authorizedUser,
                    trId,
                    tr.getEmployeecontract().getId(),
                    tr.getEmployeeorder().getId(),
                    tr.getReferenceday().getRefdate(),
                    reportForm.getComment(),
                    Boolean.TRUE.equals(reportForm.getTraining()),
                    reportForm.getSelectedDurationHour(),
                    reportForm.getSelectedDurationMinute(),
                    tr.getSortofreport(),
                    reportForm.getCosts()
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
            showDailyReportForm.setEmployeeContractId(ec.getId());
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
            List<Timereport> timereports = (List<Timereport>) request.getSession().getAttribute("timereports");

            request.getSession().setAttribute("labortime", timereportHelper.calculateLaborTime(timereports));
            request.getSession().setAttribute("maxlabortime", timereportHelper.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
            request.getSession().setAttribute("dailycosts", timereportHelper.calculateDailyCosts(timereports));

            Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(tr.getReferenceday().getRefdate(), ec.getId());

            // save values from the data base into form-bean, when working day != null
            if (workingday != null) {

                //show break time, quitting time and working day ends on the showdailyreport.jsp
                request.getSession().setAttribute("visibleworkingday", true);

                showDailyReportForm.setSelectedWorkHourBegin(workingday.getStarttimehour());
                showDailyReportForm.setSelectedWorkMinuteBegin(workingday.getStarttimeminute());
                showDailyReportForm.setSelectedBreakHour(workingday.getBreakhours());
                showDailyReportForm.setSelectedBreakMinute(workingday.getBreakminutes());
            } else {

                //show�t break time, quitting time and working day ends on the showdailyreport.jsp
                request.getSession().setAttribute("visibleworkingday", false);

                showDailyReportForm.setSelectedWorkHourBegin(0);
                showDailyReportForm.setSelectedWorkMinuteBegin(0);
                showDailyReportForm.setSelectedBreakHour(0);
                showDailyReportForm.setSelectedBreakMinute(0);
            }

            request.getSession().setAttribute("quittingtime", timereportHelper.calculateQuittingTime(workingday, request, "quittingtime"));

            //refresh overtime
            refreshVacationAndOvertime(request, ec);

            return mapping.findForward("success");
        }

        return mapping.findForward("error");
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }
}
