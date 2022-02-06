package org.tb.action.dailyreport;

import static org.tb.GlobalConstants.SORT_OF_REPORT_WORK;

import java.io.IOException;
import java.sql.Date;
import java.util.List;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.GlobalConstants;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Publicholiday;
import org.tb.bdom.Timereport;
import org.tb.bdom.Workingday;
import org.tb.helper.AfterLogin;
import org.tb.helper.TimereportHelper;
import org.tb.helper.VacationViewer;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.WorkingdayDAO;
import org.tb.util.DateUtils;
import org.tb.form.ShowDailyReportForm;
import org.tb.form.UpdateDailyReportForm;

/**
 * action class for updating a timereport directly from daily display
 *
 * @author oda
 */
@Component("/UpdateDailyReport")
@Slf4j
public class UpdateDailyReportAction extends DailyReportAction<UpdateDailyReportForm> {

    private final SuborderDAO suborderDAO;
    private final CustomerorderDAO customerorderDAO;
    private final TimereportDAO timereportDAO;
    private final PublicholidayDAO publicholidayDAO;
    private final WorkingdayDAO workingdayDAO;
    private final EmployeeorderDAO employeeorderDAO;
    private final EmployeecontractDAO employeecontractDAO;
    private final TimereportHelper timereportHelper;

    @Autowired
    public UpdateDailyReportAction(AfterLogin afterLogin, SuborderDAO suborderDAO,
        CustomerorderDAO customerorderDAO, TimereportDAO timereportDAO,
        PublicholidayDAO publicholidayDAO, WorkingdayDAO workingdayDAO,
        EmployeeorderDAO employeeorderDAO,
        EmployeecontractDAO employeecontractDAO, TimereportHelper timereportHelper) {
        super(afterLogin);
        this.suborderDAO = suborderDAO;
        this.customerorderDAO = customerorderDAO;
        this.timereportDAO = timereportDAO;
        this.publicholidayDAO = publicholidayDAO;
        this.workingdayDAO = workingdayDAO;
        this.employeeorderDAO = employeeorderDAO;
        this.employeecontractDAO = employeecontractDAO;
        this.timereportHelper = timereportHelper;
    }

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, UpdateDailyReportForm reportForm, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (request.getParameter("trId") != null) {
            long trId = Long.parseLong(request.getParameter("trId"));
            Timereport tr = timereportDAO.getTimereportById(trId);
            Date theDate = tr.getReferenceday().getRefdate();
            Employeecontract ec = tr.getEmployeecontract();

            ActionMessages errorMessages = validateFormData(request, reportForm, theDate, tr);
            if (errorMessages.size() > 0) {
                return mapping.getInputForward();
            }

            tr.setTaskdescription(reportForm.getComment());
            tr.setDurationhours(reportForm.getSelectedDurationHour());
            tr.setDurationminutes(reportForm.getSelectedDurationMinute());
            tr.setCosts(reportForm.getCosts());
            tr.setTraining(reportForm.getTraining());

            Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");

            //check if report's order is vacation but not Overtime compensation
            if (tr.getSuborder().getCustomerorder().getSign().equals(GlobalConstants.CUSTOMERORDER_SIGN_VACATION)
                    && !tr.getSuborder().getSign().equals(GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
                //fill VacationView with data
                Employeeorder vacationOrder = employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(ec.getId(), tr.getSuborder().getId(), theDate);
                VacationViewer vacationView = new VacationViewer(ec);
                vacationView.setSuborderSign(vacationOrder.getSuborder().getSign());
                if (vacationOrder.getDebithours() != null) {
                    vacationView.setBudget(vacationOrder.getDebithours());
                } else { //should not happen since debit hours of yearly vacation order is generated automatically when the order is created
                    vacationOrder.setDebithours(vacationOrder.getEmployeecontract().getVacationEntitlement() * vacationOrder.getEmployeecontract().getDailyWorkingTime());
                    vacationView.setBudget(vacationOrder.getDebithours());
                }
                List<Timereport> timereports = timereportDAO.getTimereportsBySuborderIdAndEmployeeContractId(vacationOrder.getSuborder().getId(), ec.getId());
                for (Timereport timereport : timereports) {
                    if (tr.getId() != timereport.getId()) {
                        vacationView.addVacationMinutes(60 * timereport.getDurationhours());
                        vacationView.addVacationMinutes(timereport.getDurationminutes());
                    }
                }
                vacationView.addVacationMinutes(60 * tr.getDurationhours());
                vacationView.addVacationMinutes(tr.getDurationminutes());
                //check if current timereport would overrun vacation budget of corresponding year of suborder
                if (vacationView.isVacationBudgetExceeded()) {
                    request.getSession().setAttribute("vacationBudgetOverrun", true);
                    return mapping.findForward("success");
                } else {
                    request.getSession().setAttribute("vacationBudgetOverrun", false);
                    timereportDAO.save(tr, loginEmployee, true);
                }

            } else {
                // save updated report
                request.getSession().setAttribute("vacationBudgetOverrun", false);
                timereportDAO.save(tr, loginEmployee, true);
            }

            if (tr.getStatus().equalsIgnoreCase(GlobalConstants.TIMEREPORT_STATUS_CLOSED) && loginEmployee.getStatus().equalsIgnoreCase("adm")) {
                // recompute overtimeStatic and store it in employeecontract
                double otStatic = timereportHelper.calculateOvertime(ec.getValidFrom(), ec.getReportAcceptanceDate(), ec, true);
                ec.setOvertimeStatic(otStatic / 60.0);
                employeecontractDAO.save(ec, loginEmployee);
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

    /**
     * validates the form data (syntax and logic)
     */
    private ActionMessages validateFormData(HttpServletRequest request,
                                            UpdateDailyReportForm reportForm,
                                            Date theDate,
                                            Timereport theTimereport) {

        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }

        // if sort of report is not 'W' reports are only allowed for workdays
        // e.g., vacation cannot be set on a Sunday
        if (!theTimereport.getSortofreport().equals(SORT_OF_REPORT_WORK)) {
            boolean valid = DateUtils.isWeekday(theDate);

            // checks for public holidays
            if (valid) {
                Optional<Publicholiday> publicHoliday = publicholidayDAO.getPublicHoliday(theDate);
                if (publicHoliday.isPresent()) {
                    valid = false;
                }
            }

            if (!valid) {
                errors.add("sortOfReport", new ActionMessage("form.timereport.error.sortofreport.invalidday"));
            }
        }

        if (theTimereport.getSortofreport().equals(SORT_OF_REPORT_WORK)) {
            // check costs format		
            if (!GenericValidator.isDouble(reportForm.getCosts().toString()) ||
                    !GenericValidator.isInRange(reportForm.getCosts(),
                            0.0, GlobalConstants.MAX_COSTS)) {
                errors.add("costs", new ActionMessage("form.timereport.error.costs.wrongformat"));
            }
        }

        // check comment length
        if (!GenericValidator.maxLength(reportForm.getComment(), GlobalConstants.COMMENT_MAX_LENGTH)) {
            errors.add("comment", new ActionMessage("form.timereport.error.comment.toolarge"));
        }

        // check if comment is necessary
        Boolean commentnecessary = theTimereport.getSuborder().getCommentnecessary();
        if (commentnecessary && (reportForm.getComment() == null || reportForm.getComment().trim().equals(""))) {
            errors.add("comment", new ActionMessage("form.timereport.error.comment.necessary"));
        }

        saveErrors(request, errors);

        return errors;
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }
}
