package org.tb.action.dailyreport;

import static java.lang.Boolean.TRUE;
import static org.tb.GlobalConstants.EMPLOYEE_STATUS_ADM;
import static org.tb.GlobalConstants.EMPLOYEE_STATUS_BL;
import static org.tb.GlobalConstants.EMPLOYEE_STATUS_PV;
import static org.tb.GlobalConstants.MINUTES_PER_HOUR;
import static org.tb.GlobalConstants.MINUTE_INCREMENT;
import static org.tb.GlobalConstants.SORT_OF_REPORT_WORK;

import java.io.IOException;
import java.sql.Date;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
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
import org.tb.bdom.AuthorizedUser;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Publicholiday;
import org.tb.bdom.Referenceday;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;
import org.tb.bdom.Workingday;
import org.tb.exception.AuthorizationException;
import org.tb.exception.BusinessRuleException;
import org.tb.exception.InvalidDataException;
import org.tb.form.AddDailyReportForm;
import org.tb.form.ShowDailyReportForm;
import org.tb.helper.AfterLogin;
import org.tb.helper.CustomerorderHelper;
import org.tb.helper.SuborderHelper;
import org.tb.helper.TimereportHelper;
import org.tb.helper.VacationViewer;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.ReferencedayDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.WorkingdayDAO;
import org.tb.service.TimereportService;
import org.tb.util.DateUtils;

/**
 * Action class for a timereport to be stored permanently.
 *
 * @author oda
 */
@Component("/StoreDailyReport")
@Slf4j
public class StoreDailyReportAction extends DailyReportAction<AddDailyReportForm> {

    private final EmployeecontractDAO employeecontractDAO;
    private final SuborderDAO suborderDAO;
    private final CustomerorderDAO customerorderDAO;
    private final TimereportDAO timereportDAO;
    private final ReferencedayDAO referencedayDAO;
    private final PublicholidayDAO publicholidayDAO;
    private final WorkingdayDAO workingdayDAO;
    private final EmployeeorderDAO employeeorderDAO;
    private final SuborderHelper suborderHelper;
    private final CustomerorderHelper customerorderHelper;
    private final TimereportHelper timereportHelper;
    private final TimereportService timereportService;

    @Autowired
    public StoreDailyReportAction(AfterLogin afterLogin, EmployeecontractDAO employeecontractDAO,
        SuborderDAO suborderDAO, CustomerorderDAO customerorderDAO, TimereportDAO timereportDAO,
        ReferencedayDAO referencedayDAO, PublicholidayDAO publicholidayDAO,
        WorkingdayDAO workingdayDAO, EmployeeorderDAO employeeorderDAO,
        SuborderHelper suborderHelper, CustomerorderHelper customerorderHelper,
        TimereportHelper timereportHelper, TimereportService timereportService) {
        super(afterLogin);
        this.employeecontractDAO = employeecontractDAO;
        this.suborderDAO = suborderDAO;
        this.customerorderDAO = customerorderDAO;
        this.timereportDAO = timereportDAO;
        this.referencedayDAO = referencedayDAO;
        this.publicholidayDAO = publicholidayDAO;
        this.workingdayDAO = workingdayDAO;
        this.employeeorderDAO = employeeorderDAO;
        this.suborderHelper = suborderHelper;
        this.customerorderHelper = customerorderHelper;
        this.timereportHelper = timereportHelper;
        this.timereportService = timereportService;
    }

    @SuppressWarnings("unchecked")
    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddDailyReportForm form, HttpServletRequest request, HttpServletResponse response) throws IOException {
        boolean refreshOrders = false;
        boolean refreshSuborders = false;
        boolean refreshWorkdayAvailability = false;

        // TODO split logic into different methods
        Employeecontract employeeContract = getEmployeeContractAndSetSessionVars(request);

        // task for setting the date
        if (request.getParameter("task") != null && request.getParameter("task").equals("setDate")) {
            int howMuch = Integer.parseInt(request.getParameter("howMuch"));
            String referenceDayFormValue = form.getReferenceday();
            java.util.Date calculatedReferenceDay;

            /* check if today is to be set or not, 0 indicates "set to today" */
            if(howMuch == 0) {
                calculatedReferenceDay = DateUtils.today();
            } else {
                calculatedReferenceDay = DateUtils.parse(referenceDayFormValue, DateUtils.today());
                Calendar calculatedReferenceDayCalendar = Calendar.getInstance();
                calculatedReferenceDayCalendar.setTime(calculatedReferenceDay);
                calculatedReferenceDayCalendar.add(Calendar.DAY_OF_MONTH, howMuch);
                calculatedReferenceDay = calculatedReferenceDayCalendar.getTime();
            }

            String calculatedReferenceDayFormValue = DateUtils.format(calculatedReferenceDay);
            request.getSession().setAttribute("referenceday", calculatedReferenceDayFormValue);
            form.setReferenceday(calculatedReferenceDayFormValue);
            refreshOrders = true;
            refreshSuborders = true;
            refreshWorkdayAvailability = true;
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("refreshOrders")) {
            refreshOrders = true;
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("refreshSuborders")) {
            refreshSuborders = true;
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("adjustBeginTime")) {
            refreshWorkdayAvailability = true;
            Double dailyWorkingTime = employeeContract.getDailyWorkingTime();
            dailyWorkingTime *= 60;
            int dailyWorkingTimeMinutes = dailyWorkingTime.intValue();
            Customerorder selectedOrder = customerorderDAO.getCustomerorderById(form.getOrderId());
            boolean standardOrder = customerorderHelper.isOrderStandard(selectedOrder);
            Boolean workingDayAvailable = (Boolean) request.getSession().getAttribute("workingDayIsAvailable");
            if (TRUE == workingDayAvailable) {
                java.sql.Date referenceDay = DateUtils.parseSqlDate(form.getReferenceday(), DateUtils.todaySqlDate());
                Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(referenceDay, employeeContract.getId());

                // set the begin time as the end time of the latest existing timereport of current employee
                // for current day. If no other reports exist so far, set standard begin time (0800).
                int[] beginTime = timereportHelper.determineBeginTimeToDisplay(employeeContract.getId(), referenceDay, workingday);
                int beginHours = beginTime[0];
                int beginMinutes = beginTime[1];
                // round down to next minute increment
                beginMinutes = beginMinutes / MINUTE_INCREMENT * MINUTE_INCREMENT;
                form.setSelectedHourBegin(beginHours);
                form.setSelectedMinuteBegin(beginMinutes);

                // determine end time
                int currentHours = DateUtils.getCurrentHours();
                int currentMinutes = DateUtils.getCurrentMinutes();
                // round down to next minute increment
                currentMinutes = currentMinutes / MINUTE_INCREMENT * MINUTE_INCREMENT;
                Date today = DateUtils.todaySqlDate();
                if (standardOrder) {
                    int minutes = form.getSelectedHourBegin() * MINUTES_PER_HOUR + form.getSelectedMinuteBegin();
                    minutes += dailyWorkingTimeMinutes;
                    int hours = minutes / MINUTES_PER_HOUR;
                    minutes = minutes % MINUTES_PER_HOUR;
                    // round down to next minute increment
                    minutes = minutes / MINUTE_INCREMENT * MINUTE_INCREMENT;
                    form.setSelectedMinuteEnd(minutes);
                    form.setSelectedHourEnd(hours);
                } else if (workStartedEarlier(beginHours, beginMinutes, currentHours, currentMinutes) && referenceDay.equals(today)) {
                    form.setSelectedMinuteEnd(currentMinutes);
                    form.setSelectedHourEnd(currentHours);
                } else {
                    form.setSelectedHourEnd(beginHours);
                    form.setSelectedMinuteEnd(beginMinutes);
                }
                timereportHelper.refreshHours(form);
            } else {
                // TODO wird dieser Code je durchlaufen?
                if (standardOrder) {

                    int hours = dailyWorkingTimeMinutes / MINUTES_PER_HOUR;
                    int minutes = dailyWorkingTimeMinutes % MINUTES_PER_HOUR;
                    // round down to next minute increment
                    minutes = minutes / MINUTE_INCREMENT * MINUTE_INCREMENT;

                    form.setSelectedHourDuration(hours);
                    form.setSelectedMinuteDuration(minutes);
                }
            }
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("adjustSuborderSignChanged")) {

            // refresh suborder sign/description select menus
            suborderHelper.adjustSuborderSignChanged(request.getSession(), form);
            Suborder suborder = suborderDAO.getSuborderById(form.getSuborderSignId());
            request.getSession().setAttribute("currentSuborderSign", suborder.getSign());
            setSubOrder(suborder, request, form);
        }

        if (request.getParameter("task") != null && request.getParameter("task").equalsIgnoreCase("updateSortOfReport")) {
            // updates the sort of report
            request.getSession().setAttribute("report", form.getSortOfReport());
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("refreshHours")) {
            // refreshes the hours displayed after a change of duration period
            timereportHelper.refreshHours(form);
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("refreshPeriod")) {
            // refreshes the duration period after a change of begin/end times
            timereportHelper.refreshPeriod(request, form);
        }

        if (refreshOrders) {
            customerorderHelper.refreshOrders(request, form);
        }

        if (refreshSuborders) {
            // refresh suborders to be displayed in the select menu
            String defaultSuborderIndexStr = request.getParameter("defaultSuborderIndex");
            suborderHelper.refreshSuborders(request, form, defaultSuborderIndexStr);

            // check if we can prefill the form with daily working time - this helps for standard orders like URLAUB
            Customerorder selectedOrder = customerorderDAO.getCustomerorderById(form.getOrderId());
            boolean standardOrder = customerorderHelper.isOrderStandard(selectedOrder);
            if (standardOrder) {

                Double dailyWorkingTime = employeeContract.getDailyWorkingTime();
                dailyWorkingTime *= 60;
                int dailyWorkingTimeMinutes = dailyWorkingTime.intValue();
                int hours = dailyWorkingTimeMinutes / MINUTES_PER_HOUR;
                int minutes = dailyWorkingTimeMinutes % MINUTES_PER_HOUR;
                // round down to next minute increment
                minutes = minutes / MINUTE_INCREMENT * MINUTE_INCREMENT;

                form.setSelectedHourDuration(hours);
                form.setSelectedMinuteDuration(minutes);
            }
        }

        if (refreshWorkdayAvailability) {
            // search for adequate workingday and set status in session
            java.sql.Date selectedDate = DateUtils.parseSqlDate(form.getReferenceday(), DateUtils.todaySqlDate());
            Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(selectedDate, employeeContract.getId());
            boolean workingDayIsAvailable = workingday != null && DateUtils.todaySqlDate().equals(selectedDate);
            request.getSession().setAttribute("workingDayIsAvailable", workingDayIsAvailable);
        }

        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("save") ||
                request.getParameter("trId") != null) {

            ActionMessages errors = getErrors(request);
            if (errors == null) {
                errors = new ActionMessages();
            }

            // 'main' task - prepare everything to store the report.
            // I.e., copy properties from the form into the timereport before saving.
            Employeecontract employeecontract = employeecontractDAO.getEmployeeContractById(form.getEmployeeContractId());
            double hours = timereportHelper.calculateTime(form);

            java.util.Date referencedayRefDate;
            try {
                referencedayRefDate = DateUtils.parse(form.getReferenceday());
            } catch (ParseException e) {
                throw new RuntimeException("Klaus has no idea why this code is so ugly", e);
            }

            List<Employeeorder> employeeorders = employeeorderDAO
                .getEmployeeOrderByEmployeeContractIdAndSuborderIdAndDate2(
                    form.getEmployeeContractId(),
                    form.getSuborderSignId(),
                    referencedayRefDate
                );
            long employeeorderId = -1;
            if(!employeeorders.isEmpty()) {
                employeeorderId = employeeorders.get(0).getId();
            }

            // TODO get authorizedUser from session
            Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
            AuthorizedUser authorizedUser = new AuthorizedUser(
                loginEmployee.getId(),
                loginEmployee.getSign(),
                loginEmployee.getStatus().equals(EMPLOYEE_STATUS_ADM),
                loginEmployee.getStatus().equals(EMPLOYEE_STATUS_BL) || loginEmployee.getStatus().equals(EMPLOYEE_STATUS_PV)
            );

            long timeReportId = form.getId();
            // TODO maybe find a better way to identify timereports in edit
            if(timeReportId > 0) {
                try {
                    timereportService.updateTimereport(
                        authorizedUser,
                        timeReportId,
                        form.getEmployeeContractId(),
                        employeeorderId,
                        referencedayRefDate,
                        form.getComment(),
                        Boolean.TRUE.equals(form.getTraining()),
                        form.getSelectedHourDuration(),
                        form.getSelectedMinuteDuration(),
                        SORT_OF_REPORT_WORK,
                        form.getCosts()
                    );
                } catch (AuthorizationException | BusinessRuleException | InvalidDataException e) {
                    addToErrors(request, e.getErrorCode());
                    return mapping.getInputForward();
                }
            } else {
                try {
                    timereportService.createTimereports(
                        authorizedUser,
                        form.getEmployeeContractId(),
                        employeeorderId,
                        referencedayRefDate,
                        form.getComment(),
                        Boolean.TRUE.equals(form.getTraining()),
                        form.getSelectedHourDuration(),
                        form.getSelectedMinuteDuration(),
                        SORT_OF_REPORT_WORK,
                        form.getCosts(),
                        Math.max(form.getNumberOfSerialDays(), 1) // ensure at least one
                    );
                } catch (AuthorizationException | BusinessRuleException | InvalidDataException e) {
                    addToErrors(request, e.getErrorCode());
                    return mapping.getInputForward();
                }
            }

            final Timereport timereport;
            if (request.getSession().getAttribute("trId") != null) {
                timeReportId = Long.parseLong(request.getSession().getAttribute("trId").toString());
                timereport = timereportDAO.getTimereportById(timeReportId);
            } else if (request.getParameter("trId") != null) {
                // edited report from daily overview
                timeReportId = Long.parseLong(request.getParameter("trId"));
                timereport = timereportDAO.getTimereportById(timeReportId);
            } else {
                // new report
                timereport = new Timereport();
                timereport.setStatus(GlobalConstants.TIMEREPORT_STATUS_OPEN);
            }

            ActionMessages errorMessages = validateFormData(request, form, timeReportId, employeecontract.getId(), hours, errors);
            if (errorMessages.size() > 0) {
                return mapping.getInputForward();
            }

            // FIXME get Employeeorder from form and DAO not from session!
            Employeeorder employeeorder = (Employeeorder) request.getSession().getAttribute("saveEmployeeOrder");

            timereport.setTaskdescription(form.getComment());
            timereport.setEmployeecontract(employeecontract);
            timereport.setTraining(form.getTraining());

            // currently every timereport has sortOfReport = SORT_OF_REPORT_WORK
            if (!form.getSortOfReport().equals(SORT_OF_REPORT_WORK)) {
                double durationMinutes = employeecontract.getDailyWorkingTime() % 1 * MINUTES_PER_HOUR; // calc minutes from decimal part
                timereport.setDurationhours(employeecontract.getDailyWorkingTime().intValue()); // get the full hours part
                timereport.setDurationminutes((int)durationMinutes);
            } else {
                timereport.setDurationhours(form.getSelectedHourDuration());
                timereport.setDurationminutes(form.getSelectedMinuteDuration());
            }

            timereport.setSortofreport(SORT_OF_REPORT_WORK);

            if (timereport.getReferenceday() == null ||
                    timereport.getReferenceday().getRefdate() == null ||
                    !timereport.getReferenceday().getRefdate().equals(referencedayRefDate)) {
                // if timereport is new
                Referenceday referenceDay = referencedayDAO.getReferencedayByDate(referencedayRefDate);
                if (referenceDay == null) {
                    // new referenceday to be added in database
                    referencedayDAO.addReferenceday(referencedayRefDate);
                    referenceDay = referencedayDAO.getReferencedayByDate(referencedayRefDate);
                }
                timereport.setReferenceday(referenceDay);
            }

            // set employee order
            timereport.setEmployeeorder(employeeorder);
            request.getSession().removeAttribute("saveEmployeeOrder");

            if (form.getSortOfReport().equals(SORT_OF_REPORT_WORK)) {
                timereport.setCosts(form.getCosts());
                // TODO this is redundant to employeeorder
                timereport.setSuborder(suborderDAO.getSuborderById(form.getSuborderSignId()));
            } else {
                // 'special' reports: set suborder in timereport to null.
                timereport.setSuborder(null);
                timereport.setCosts(0.0);
            }

            List<Timereport> existingTimereports = timereportDAO.getTimereportsByDateAndEmployeeContractId(
                employeecontract.getId(), timereport.getReferenceday().getRefdate());
            boolean reportFoundInList = existingTimereports.stream()
                .anyMatch(existingTimereport -> existingTimereport.getId() == timereport.getId());
            if (!reportFoundInList) {
                if (existingTimereports.isEmpty()) {
                    timereport.setSequencenumber(1);
                } else {
                    int lastindex = existingTimereports.size() - 1;
                    timereport.setSequencenumber(existingTimereports.get(lastindex).getSequencenumber() + 1);
                }
            }

            // FIXME remove firstday - better fall back on day before contract starts when no release date or acceptance date is found
            java.util.Date releaseDate = employeecontract.getReportReleaseDate();
            if (releaseDate == null) {
                releaseDate = employeecontract.getValidFrom();
            }
            java.util.Date acceptanceDate = employeecontract.getReportAcceptanceDate();
            if (acceptanceDate == null) {
                acceptanceDate = employeecontract.getValidFrom();
            }
            java.util.Date refDate = timereport.getReferenceday().getRefdate();

            boolean firstday = false;
            if (!releaseDate.after(employeecontract.getValidFrom())) {
                if (!refDate.after(employeecontract.getValidFrom())) {
                    firstday = true;
                }
            }

            // TODO check: should fail when performed by a non manager
            if (!refDate.after(releaseDate) && !firstday) {
                timereport.setStatus(GlobalConstants.TIMEREPORT_STATUS_COMMITED);
            }
            // TODO check: should fail when performed by a non admin
            if (!refDate.after(acceptanceDate) && !firstday) {
                timereport.setStatus(GlobalConstants.TIMEREPORT_STATUS_CLOSED);
            }

            int numberOfSerialDays = form.getNumberOfSerialDays();

            // is the timereport a booking for vacation?
            if (timereport.getSuborder() != null
                    && timereport.getSuborder().getCustomerorder().getSign().equals(GlobalConstants.CUSTOMERORDER_SIGN_VACATION)
                    && !timereport.getSuborder().getSign().equals(GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
                //fill VacationView with data
                Employeeorder vacationOrder = employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(employeecontract.getId(), timereport.getSuborder().getId(), refDate);
                VacationViewer vacationView = new VacationViewer(employeecontract);
                vacationView.setSuborderSign(vacationOrder.getSuborder().getSign());
                if (vacationOrder.getDebithours() != null) {
                    vacationView.setBudget(vacationOrder.getDebithours());
                } else { //should not happen since debit hours of yearly vacation order is generated automatically when the order is created
                    // FIXME remove this???? Maybe we need to fix this ?!? Aktuell wird bei Vertragswechseln der Urlaubsanspruch falsch berechnet. Das kann hieran liegen!!!
                    vacationOrder.setDebithours(vacationOrder.getEmployeecontract().getVacationEntitlement() * vacationOrder.getEmployeecontract().getDailyWorkingTime());
                    vacationView.setBudget(vacationOrder.getDebithours());
                }
                List<Timereport> existingVacationTimereports = timereportDAO.getTimereportsBySuborderIdAndEmployeeContractId(vacationOrder.getSuborder().getId(), employeecontract.getId());
                for (Timereport existingVacationTimereport : existingVacationTimereports) {
                    if (existingVacationTimereport.getId() != existingVacationTimereport.getId()) {
                        vacationView.addVacationMinutes(MINUTES_PER_HOUR * existingVacationTimereport.getDurationhours());
                        vacationView.addVacationMinutes(existingVacationTimereport.getDurationminutes());
                    }
                }

                numberOfSerialDays = Math.max(numberOfSerialDays, 1); // if nothing is selected, set to 1
                vacationView.addVacationMinutes(MINUTES_PER_HOUR * numberOfSerialDays * timereport.getDurationhours());
                vacationView.addVacationMinutes(numberOfSerialDays * timereport.getDurationminutes());

                // FIXME move to validation method as this is the validation i was looking for
                //check if current timereport/serial reports would overrun vacation budget of corresponding year of suborder
                if (vacationView.isVacationBudgetExceeded()) {
                    request.getSession().setAttribute("vacationBudgetOverrun", true);
                    return mapping.findForward("showDaily");
                }
            }
            request.getSession().setAttribute("vacationBudgetOverrun", false);
            if (numberOfSerialDays > 1) {
                if (timereport.getId() != 0) {
                    // FIXME why that??? Maybe we update timereports with this action, too? check that!
                    timereportDAO.deleteTimereportById(timereport.getId());
                }
                Date firstTimereportReferenceDay = timereport.getReferenceday().getRefdate();

                List<java.util.Date> calculatedReferenceDays = timereportHelper.getDatesForTimePeriod(firstTimereportReferenceDay, numberOfSerialDays);
                for (java.util.Date calculatedReferenceDay : calculatedReferenceDays) {
                    java.sql.Date calculatedReferenceDaySqlDate = DateUtils.toSqlDate(calculatedReferenceDay);
                    // FIXME get or create reference day?
                    Referenceday referenceDay = referencedayDAO.getReferencedayByDate(calculatedReferenceDaySqlDate);
                    if (referenceDay == null) {
                        // new referenceday to be added in database
                        referencedayDAO.addReferenceday(calculatedReferenceDaySqlDate);
                        referenceDay = referencedayDAO.getReferencedayByDate(calculatedReferenceDaySqlDate);
                    }
                    Timereport serialReport = timereport.getTwin();
                    // TODO sequencenumber is wrong
                    serialReport.setReferenceday(referenceDay);
                    timereportDAO.save(serialReport, loginEmployee, true);
                }
            } else {
                timereportDAO.save(timereport, loginEmployee, true);
            }

            if (timereport.getStatus().equalsIgnoreCase(GlobalConstants.TIMEREPORT_STATUS_CLOSED) && loginEmployee.getStatus().equalsIgnoreCase("adm")) {
                // recompute overtimeStatic and store it in employeecontract
                double otStatic = timereportHelper.calculateOvertime(employeecontract.getValidFrom(), employeecontract.getReportAcceptanceDate(),
                        employeecontract, true);
                employeecontract.setOvertimeStatic(otStatic / 60.0);
                employeecontractDAO.save(employeecontract, loginEmployee);
            }

            request.getSession().setAttribute("currentDay", DateUtils.getDayString(referencedayRefDate));
            request.getSession().setAttribute("currentMonth", DateUtils.getMonthShortString(referencedayRefDate));
            request.getSession().setAttribute("currentYear", DateUtils.getYearString(referencedayRefDate));
            List<Timereport> reports;
            if (request.getSession().getAttribute("trId") != null) {
                request.getSession().removeAttribute("trId");
            }
            Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(timereport.getReferenceday().getRefdate(), employeecontract.getId());

            if (request.getParameter("continue") == null || !Boolean.parseBoolean(request.getParameter("continue"))) {
                // FIXME geht das nicht leichter?
                // set new ShowDailyReportForm with saved filter settings
                ShowDailyReportForm continueForm = new ShowDailyReportForm();

                java.sql.Date referenceday = DateUtils.parseSqlDate(form.getReferenceday(), DateUtils.todaySqlDate());
                // TODO pruefen, warum diese Felder gebraucht werden
                continueForm.setDay(DateUtils.getDayString(referenceday));
                continueForm.setMonth(DateUtils.getMonthShortString(referenceday));
                continueForm.setYear(DateUtils.getYearString(referenceday));

                continueForm.setStartdate(continueForm.getYear() + "-" + DateUtils.getMonthMMStringFromShortstring(continueForm.getMonth()) + "-" + continueForm.getDay());
                if (request.getSession().getAttribute("lastLastMonth") != null) {
                    continueForm.setLastday((String) request.getSession().getAttribute("lastLastDay"));
                    continueForm.setLastmonth((String) request.getSession().getAttribute("lastLastMonth"));
                    continueForm.setLastyear((String) request.getSession().getAttribute("lastLastYear"));
                } else {
                    continueForm.setLastday(DateUtils.getDayString(referenceday));
                    continueForm.setLastmonth(DateUtils.getMonthShortString(referenceday));
                    continueForm.setLastyear(DateUtils.getYearString(referenceday));
                }
                continueForm.setEnddate(continueForm.getLastyear() + "-" + DateUtils.getMonthMMStringFromShortstring(continueForm.getLastmonth()) + "-"
                        + continueForm.getLastday());
                request.getSession().removeAttribute("lastCurrentDay");
                request.getSession().removeAttribute("lastCurrentMonth");
                request.getSession().removeAttribute("lastCurrentYear");
                request.getSession().removeAttribute("lastLastDay");
                request.getSession().removeAttribute("lastLastMonth");
                request.getSession().removeAttribute("lastLastYear");

                if (request.getSession().getAttribute("lastView") != null) {
                    continueForm.setView((String) request.getSession().getAttribute("lastView"));
                } else {
                    continueForm.setView(GlobalConstants.VIEW_DAILY);
                }
                request.getSession().removeAttribute("lastView");
                continueForm.setOrder((String) request.getSession().getAttribute("lastOrder"));
                if (request.getSession().getAttribute("lastSuborderId") != null) {
                    continueForm.setSuborderId((Long) request.getSession().getAttribute("lastSuborderId"));
                } else {
                    continueForm.setSuborderId(-1);
                }
                if (request.getSession().getAttribute("lastEmployeeContractId") != null) {
                    continueForm.setEmployeeContractId((Long) request.getSession().getAttribute("lastEmployeeContractId"));
                } else {
                    continueForm.setEmployeeContractId(loginEmployee.getId());
                }
                request.getSession().removeAttribute("lastSuborderId");
                request.getSession().removeAttribute("lastOrder");
                request.getSession().removeAttribute("lastEmployeeContractId");

                // get updated list of timereports from DB
                refreshTimereports(
                        request,
                        continueForm,
                        customerorderDAO,
                        timereportDAO,
                        employeecontractDAO,
                        suborderDAO,
                        employeeorderDAO
                );
                reports = (List<Timereport>) request.getSession().getAttribute("timereports");
                request.getSession().setAttribute("suborderFilerId", continueForm.getSuborderId());

                request.getSession().setAttribute("labortime", timereportHelper.calculateLaborTime(reports));
                request.getSession().setAttribute("maxlabortime", timereportHelper.checkLaborTimeMaximum(existingTimereports, GlobalConstants.MAX_HOURS_PER_DAY));
                request.getSession().setAttribute("dailycosts", timereportHelper.calculateDailyCosts(existingTimereports));
                request.getSession().setAttribute("quittingtime", timereportHelper.calculateQuittingTime(workingday, request, "quittingtime"));

                //calculate Working Day End
                request.getSession().setAttribute("workingDayEnds", timereportHelper.calculateQuittingTime(workingday, request, "workingDayEnds"));

                request.getSession().setAttribute("years", DateUtils.getYearsToDisplay());
                request.getSession().setAttribute("days", DateUtils.getDaysToDisplay());
                request.getSession().setAttribute("months", DateUtils.getMonthsToDisplay());
                request.getSession().setAttribute("hours", DateUtils.getHoursToDisplay());
                request.getSession().setAttribute("breakhours", DateUtils.getCompleteHoursToDisplay());
                request.getSession().setAttribute("breakminutes", DateUtils.getMinutesToDisplay());
                request.getSession().setAttribute("hoursDuration", DateUtils.getHoursDurationToDisplay());
                request.getSession().setAttribute("minutes", DateUtils.getMinutesToDisplay());

                // save values from the data base into form-bean, when working day != null
                if (workingday != null) {
                    // show break time, quitting time and working day ends on the
                    // showdailyreport.jsp
                    request.getSession().setAttribute("visibleworkingday", true);
                    continueForm.setSelectedWorkHourBegin(workingday.getStarttimehour());
                    continueForm.setSelectedWorkMinuteBegin(workingday.getStarttimeminute());
                    continueForm.setSelectedBreakHour(workingday.getBreakhours());
                    continueForm.setSelectedBreakMinute(workingday.getBreakminutes());
                } else {
                    // don't show break time, quitting time and working day ends on
                    // the showdailyreport.jsp
                    request.getSession().setAttribute("visibleworkingday", false);
                    continueForm.setSelectedWorkHourBegin(0);
                    continueForm.setSelectedWorkMinuteBegin(0);
                    continueForm.setSelectedBreakHour(0);
                    continueForm.setSelectedBreakMinute(0);
                }
                // refresh overtime and vacation
                refreshVacationAndOvertime(request, employeecontract);
                return mapping.findForward("showDaily");

            } else { // Continue = true

                java.util.Date selectedDate = getSelectedDateFromRequest(request);

                //deleting comment, costs and days of serialBookings in the addDailyReport-Form
                form.setComment("");
                form.setCosts(0.0);
                form.setNumberOfSerialDays(0);

                if (workingday != null) {
                    int[] beginTime = timereportHelper.determineBeginTimeToDisplay(employeecontract.getId(), selectedDate, workingday);
                    int beginHours = beginTime[0];
                    int beginMinutes = beginTime[1];
                    form.setSelectedHourBegin(beginHours);
                    form.setSelectedMinuteBegin(beginMinutes);
                    form.setNumberOfSerialDays(0);
                    java.util.Date today = DateUtils.today();
                    int currentHours = DateUtils.getCurrentHours();
                    int currentMinutes = DateUtils.getCurrentMinutes();
                    // round to next minute increment
                    currentMinutes = currentMinutes / MINUTE_INCREMENT * MINUTE_INCREMENT;

                    if (workStartedEarlier(beginHours, beginMinutes, currentHours, currentMinutes) && selectedDate.equals(today)) {
                        form.setSelectedMinuteEnd(currentMinutes);
                        form.setSelectedHourEnd(currentHours);
                    } else {
                        form.setSelectedMinuteEnd(beginHours);
                        form.setSelectedHourEnd(beginMinutes);
                    }
                    timereportHelper.refreshHours(form);

                } else {
                    form.setSelectedHourDuration(0);
                    form.setSelectedMinuteDuration(0);
                }

                // load orders and suborders
                List<Customerorder> orders = customerorderDAO.getCustomerordersWithValidEmployeeOrders(employeecontract.getId(), selectedDate);
                List<Suborder> theSuborders;
                if (!orders.isEmpty()) {
                    long orderId = form.getOrderId();
                    if (orderId == 0) {
                        orderId = orders.get(0).getId();
                    }
                    theSuborders = suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(employeecontract.getId(), orderId, selectedDate);
                } else {
                    theSuborders = Collections.emptyList();
                }

                request.getSession().setAttribute("orders", orders);
                request.getSession().setAttribute("suborders", theSuborders);

                return mapping.findForward("addDaily");
            }

        }
        if (request.getParameter("task") != null && request.getParameter("task").equals("back")) {
            // go back
            request.getSession().removeAttribute("trId");
            form.reset(mapping, request);
        }
        if (request.getParameter("task") != null && request.getParameter("task").equals("reset")) {
            // reset form
            doResetActions(mapping, request, form);
        }

        return mapping.getInputForward();
    }

    private Employeecontract getEmployeeContractAndSetSessionVars(HttpServletRequest request) {
        Employeecontract employeecontract;

        if (request.getSession().getAttribute("currentEmployeeContract") != null) {
            employeecontract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
        } else {
            employeecontract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
        }
        long employeeContractId = employeecontract.getId();

        // TODO only store the id in the session, not the whole entity
        employeecontract = employeecontractDAO.getEmployeeContractById(employeeContractId);

        request.getSession().setAttribute("currentEmployee", employeecontract.getEmployee().getName());
        request.getSession().setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
        request.getSession().setAttribute("currentEmployeeContract", employeecontract);
        return employeecontract;
    }

    private void setSubOrder(@Nonnull Suborder suborder, HttpServletRequest request, AddDailyReportForm reportForm) {

        // if selected Suborder is Overtime Compensation, delete the previously automatically set daily working time
        // also make sure that overtimeCompensation is set in the session so that the duration-dropdown-menu will be disabled
        if (suborder.getSign().equalsIgnoreCase(GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
            reportForm.setSelectedHourDuration(0);
            reportForm.setSelectedMinuteDuration(0);
            if (request.getSession().getAttribute("overtimeCompensation") == null || request.getSession().getAttribute("overtimeCompensation")
                    != GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION) {
                request.getSession().setAttribute("overtimeCompensation", GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION);
            }

        }

        // if selected Suborder has a default-flag for projectbased training, set training in the form to true, so that the training-box in the jsp is checked
        if (TRUE.equals(suborder.getTrainingFlag())) {
            reportForm.setTraining(true);
        }
    }

    /**
     * resets the 'add report' form to default values
     */
    private void doResetActions(ActionMapping mapping, HttpServletRequest request, AddDailyReportForm reportForm) {
        reportForm.reset(mapping, request);

        //reset the current employee Session Variables
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");

        List<Employeecontract> employeecontracts = employeecontractDAO.getVisibleEmployeeContractsForEmployee(loginEmployee);
        String dateString = reportForm.getReferenceday();
        java.util.Date date = DateUtils.parse(dateString, DateUtils.today());

        List<Customerorder> orders = customerorderDAO.getCustomerordersWithValidEmployeeOrders(loginEmployeeContract.getId(), date);
        List<Suborder> suborders;

        //reset first order and corresponding suborders
        if (!orders.isEmpty()) {
            reportForm.setOrder(orders.get(0).getSign());
            reportForm.setOrderId(orders.get(0).getId());
            // prepare second collection of suborders sorted by description
            suborders = suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(loginEmployeeContract.getId(), orders.get(0).getId(), date);
        } else {
            reportForm.setOrder(null);
            reportForm.setOrderId(-1);
            suborders = Collections.emptyList();
        }

        if (request.getSession().getAttribute("trId") != null) {
            //get the Timereport object
            long trId = Long.parseLong(request.getSession().getAttribute("trId").toString());
            Timereport timereport = timereportDAO.getTimereportById(trId);

            //reset the rest
            reportForm.setReferenceday(timereport.getReferenceday().getRefdate().toString());
            request.getSession().setAttribute("isEdit", false);
            reportForm.setSelectedHourDuration(timereport.getDurationhours());
            reportForm.setSelectedMinuteDuration(timereport.getDurationminutes());
            reportForm.setCosts(timereport.getCosts());
            reportForm.setTraining(timereport.getTraining());
            reportForm.setComment(timereport.getTaskdescription());
        } else {
            reportForm.reset();
        }

        request.getSession().setAttribute("employeecontracts", employeecontracts);
        request.getSession().setAttribute("currentEmployee", loginEmployee.getName());
        request.getSession().setAttribute("currentEmployeeId", loginEmployee.getId());
        request.getSession().setAttribute("currentEmployeeContract", loginEmployeeContract);

        request.getSession().setAttribute("orders", orders);
        request.getSession().setAttribute("suborders", suborders);
        request.getSession().setAttribute("report", SORT_OF_REPORT_WORK);
    }

    /**
     * validates the form data (syntax and logic)
     */
    private ActionMessages validateFormData(
            HttpServletRequest request,
            AddDailyReportForm reportForm,
            long trId,
            long ecId,
            double hours,
            ActionMessages errors) {

        // check date format (must now be 'yyyy-MM-dd')
        String dateString = reportForm.getReferenceday().trim();

        boolean dateValid = DateUtils.validateDate(dateString);
        if (!dateValid) {
            errors.add("referenceday", new ActionMessage("form.timereport.error.date.wrongformat"));
            // return here - further validations do not make sense with wrong date format
            saveErrors(request, errors);
            return errors;
        }

        Date theDate = Date.valueOf(reportForm.getReferenceday());

        // check date range (must be in current or previous year)
        if (DateUtils.getCurrentYear() - DateUtils.getYear(dateString.substring(0, 4)) >= 2) {
            errors.add("referenceday", new ActionMessage("form.timereport.error.date.invalidyear"));
        }

        Boolean workingDayIsAvailable = (Boolean) request.getSession().getAttribute("workingDayIsAvailable");
        Object overtimeCompensation = request.getSession().getAttribute("overtimeCompensation");

        if (hours == 0.0 && !GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION.equals(overtimeCompensation)) {
            errors.add("selectedDuration", new ActionMessage("form.timereport.error.hours.unset"));
        }

        if (workingDayIsAvailable) {
            // end time must be later than begin time
            int begin = reportForm.getSelectedHourBegin() * 100
                    + reportForm.getSelectedMinuteBegin();
            int end = reportForm.getSelectedHourEnd() * 100
                    + reportForm.getSelectedMinuteEnd();
            if (reportForm.getSortOfReport().equals(SORT_OF_REPORT_WORK)) {
                if (begin >= end) {
                    errors.add("selectedHourBegin", new ActionMessage("form.timereport.error.endbeforebegin"));
                }
            }
        }
        // check if report types for one day are unique and if there is no time overlap with other work reports
        List<Timereport> timereports = timereportDAO.getTimereportsByDateAndEmployeeContractId(ecId, theDate);
        for (Timereport timereport : timereports) {
            if (timereport.getId() != trId) { // do not check report against itself in case of edit
                // uniqueness of types
                // actually not checked - e.g., combination of sickness and work on ONE day should be valid
                // but: vacation or sickness MUST occur only once per day
                if (!reportForm.getSortOfReport().equals(SORT_OF_REPORT_WORK) && !timereport.getSortofreport().equals(SORT_OF_REPORT_WORK)) {
                    errors.add("sortOfReport", new ActionMessage("form.timereport.error.sortofreport.special.alreadyexisting"));
                    break;
                }
            }
        }


        // check if orders/suborders are filled in case of 'W' report
        if (reportForm.getSortOfReport().equals(SORT_OF_REPORT_WORK)) {
            if (reportForm.getOrderId() <= 0) {
                errors.add("orderId", new ActionMessage("form.timereport.error.orderid.empty"));
            }
            if (reportForm.getSuborderSignId() <= 0) {
                errors.add("suborderIdDescription", new ActionMessage("form.timereport.error.suborderid.empty"));
            }
        }

        // if sort of report is not 'W' reports are only allowed for workdays
        // e.g., vacation cannot be set on a Sunday
        if (!reportForm.getSortOfReport().equals(SORT_OF_REPORT_WORK)) {
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
            } else {
                // for new report, check if other reports already exist for selected day
                if (trId == -1) {
                    List<Timereport> allReports = timereportDAO.getTimereportsByDateAndEmployeeContractId(ecId, theDate);
                    if (allReports.size() > 0) {
                        errors.add("sortOfReport", new ActionMessage("form.timereport.error.sortofreport.othersexisting"));
                    }
                }
            }

        }

        // check costs format
        if (reportForm.getSortOfReport().equals(SORT_OF_REPORT_WORK)) {
            if (!GenericValidator.isDouble(reportForm.getCosts().toString()) ||
                    !GenericValidator.isInRange(reportForm.getCosts(), 0.0, GlobalConstants.MAX_COSTS)) {
                errors.add("costs", new ActionMessage("form.timereport.error.costs.wrongformat"));
            }
        }

        // check comment length
        if (!GenericValidator.maxLength(reportForm.getComment(), GlobalConstants.COMMENT_MAX_LENGTH)) {
            errors.add("comment", new ActionMessage("form.timereport.error.comment.toolarge"));
        }

        // check if comment is necessary
        Suborder suborder = suborderDAO.getSuborderById(reportForm.getSuborderSignId());
        Boolean commentnecessary = suborder.getCommentnecessary();
        if (commentnecessary && (reportForm.getComment() == null || reportForm.getComment().trim().equals(""))) {
            errors.add("comment", new ActionMessage("form.timereport.error.comment.necessary"));
        }

        // check date vs release status
        Employeecontract employeecontract = employeecontractDAO.getEmployeeContractById(ecId);
        Employeecontract loginEmployeecontract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
        Boolean authorized = (Boolean) request.getSession().getAttribute("employeeAuthorized");
        java.util.Date refDate = DateUtils.parse(reportForm.getReferenceday(), e -> {
            throw new RuntimeException("date cannot be parsed (yyyy-MM-dd)", e);
        });
        // TODO check if it is better to set default dates one day before contract begin
        Date releaseDate = employeecontract.getReportReleaseDate();
        if (releaseDate == null) {
            releaseDate = employeecontract.getValidFrom();
        }
        Date acceptanceDate = employeecontract.getReportAcceptanceDate();
        if (acceptanceDate == null) {
            acceptanceDate = employeecontract.getValidFrom();
        }

        // check, if refDate is first day
        // TODO firstday is a bit weird
        boolean firstday;
        firstday = !releaseDate.after(employeecontract.getValidFrom()) &&
            !refDate.after(employeecontract.getValidFrom());

        if (!loginEmployeecontract.getEmployee().getSign().equals("adm")) {
            if (authorized && loginEmployeecontract.getId() != ecId) {
                if (releaseDate.before(refDate) || firstday) {
                    errors.add("release", new ActionMessage("form.timereport.error.not.released"));
                }
            } else {
                if (!releaseDate.before(refDate) && !firstday) {
                    errors.add("release", new ActionMessage("form.timereport.error.released"));
                }
            }
            if (!refDate.after(acceptanceDate) && !firstday) {
                errors.add("release", new ActionMessage("form.timereport.error.accepted"));
            }
        }
        // check for adequate employee order
        List<Employeeorder> employeeorders = employeeorderDAO.getEmployeeOrderByEmployeeContractIdAndSuborderIdAndDate2(reportForm.getEmployeeContractId(),
                reportForm.getSuborderSignId(), theDate);
        if (employeeorders == null || employeeorders.isEmpty()) {
            errors.add("employeeorder", new ActionMessage("form.timereport.error.employeeorder.notfound"));
        } else if (employeeorders.size() > 1) {
            errors.add("employeeorder", new ActionMessage("form.timereport.error.employeeorder.multiplefound"));
        } else {

            //check if all days of a serial booking are in range of the employee order			
            int numberOfLaborDays = reportForm.getNumberOfSerialDays();
            if (numberOfLaborDays > 1) {
                List<java.util.Date> dates = timereportHelper.getDatesForTimePeriod(theDate, numberOfLaborDays);
                Date lastDate = new Date(dates.get(dates.size() - 1).getTime());

                Employeeorder employeeorder = employeeorders.get(0);
                if (employeeorder.getUntilDate() != null && lastDate.after(employeeorder.getUntilDate())) {
                    errors.add("serialbooking", new ActionMessage("form.timereport.error.serialbooking.extendsemployeeorder"));
                } else {
                    request.getSession().setAttribute("saveEmployeeOrder", employeeorders.get(0));
                }

            } else {
                request.getSession().setAttribute("saveEmployeeOrder", employeeorders.get(0));
            }

        }

        saveErrors(request, errors);
        return errors;
    }

    private boolean workStartedEarlier(int beginHours, int beginMinutes, int currentHours, int currentMinutes) {
        return beginHours < currentHours || (beginHours == currentHours && beginMinutes < currentMinutes);
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }
}
