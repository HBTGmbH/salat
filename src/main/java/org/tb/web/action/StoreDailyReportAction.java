package org.tb.web.action;

import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.*;
import org.tb.GlobalConstants;
import org.tb.bdom.*;
import org.tb.helper.*;
import org.tb.persistence.*;
import org.tb.util.DateUtils;
import org.tb.web.form.AddDailyReportForm;
import org.tb.web.form.ShowDailyReportForm;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

/**
 * Action class for a timereport to be stored permanently.
 *
 * @author oda
 */
public class StoreDailyReportAction extends DailyReportAction {

    private EmployeecontractDAO employeecontractDAO;
    private SuborderDAO suborderDAO;
    private CustomerorderDAO customerorderDAO;
    private TimereportDAO timereportDAO;
    private ReferencedayDAO referencedayDAO;
    private PublicholidayDAO publicholidayDAO;
    private WorkingdayDAO workingdayDAO;
    private EmployeeDAO employeeDAO;
    private EmployeeorderDAO employeeorderDAO;
    private OvertimeDAO overtimeDAO;
    private TicketDAO ticketDAO;
    private WorklogDAO worklogDAO;
    private WorklogMemoryDAO worklogMemoryDAO;

    private SuborderHelper soHelper;
    private CustomerorderHelper coHelper;
    private JiraConnectionOAuthHelper jcHelper;

    public void setOvertimeDAO(OvertimeDAO overtimeDAO) {
        this.overtimeDAO = overtimeDAO;
    }

    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }

    public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
        this.employeeorderDAO = employeeorderDAO;
    }

    public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
        this.employeecontractDAO = employeecontractDAO;
    }

    public void setSuborderDAO(SuborderDAO suborderDAO) {
        this.suborderDAO = suborderDAO;
    }

    public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
        this.customerorderDAO = customerorderDAO;
    }

    public TimereportDAO getTimereportDAO() {
        return timereportDAO;
    }

    public void setTimereportDAO(TimereportDAO timereportDAO) {
        this.timereportDAO = timereportDAO;
    }

    public void setReferencedayDAO(ReferencedayDAO referencedayDAO) {
        this.referencedayDAO = referencedayDAO;
    }

    public void setPublicholidayDAO(PublicholidayDAO publicholidayDAO) {
        this.publicholidayDAO = publicholidayDAO;
    }

    public void setWorkingdayDAO(WorkingdayDAO workingdayDAO) {
        this.workingdayDAO = workingdayDAO;
    }

    public void setTicketDAO(TicketDAO ticketDAO) {
        this.ticketDAO = ticketDAO;
    }

    public void setWorklogDAO(WorklogDAO worklogDAO) {
        this.worklogDAO = worklogDAO;
    }

    public void setWorklogMemoryDAO(WorklogMemoryDAO worklogMemoryDAO) {
        this.worklogMemoryDAO = worklogMemoryDAO;
    }

    @SuppressWarnings("unchecked")
    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws IOException {

        Employeecontract employeeContract = null;
        if ((employeeContract = getEmployeeContractAndSetSessionVars(mapping, request)) == null) {
            request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator.");
            return mapping.findForward("error");
        }

        soHelper = new SuborderHelper();
        coHelper = new CustomerorderHelper();
        jcHelper = new JiraConnectionOAuthHelper(employeeContract.getEmployee().getSign());

        // check if special tasks initiated from the form or the daily display need to be carried out...
        AddDailyReportForm reportForm = (AddDailyReportForm) form;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
        boolean refreshTime = false;
        Ticket previousTicket = null;
        int previousDurationhours = reportForm.getSelectedHourDuration();
        int previousDurationminutes = reportForm.getSelectedMinuteDuration();
        String previousComment = reportForm.getComment();


        List<ProjectID> projectIDs = customerorderDAO.getCustomerorderById(reportForm.getOrderId()).getProjectIDs();
        request.getSession().setAttribute("projectIDExists", !projectIDs.isEmpty());

        String oauthVerifier1 = request.getParameter("oauth_verifier");
        if (oauthVerifier1 == null) {

            // if jira-Ticket-Key has been selected or entered, keep it selected even if changes are made on other parts of the site ("refresh...")
            request.getSession().setAttribute("jiraTicketKey", reportForm.getJiraTicketKey());
            request.getSession().setAttribute("newJiraTicketKey", reportForm.getNewJiraTicketKey());
        }

        // task for setting the date
        if (request.getParameter("task") != null && request.getParameter("task").equals("setDate")) {
            Integer howMuch = Integer.parseInt(request.getParameter("howMuch"));
            String datum = reportForm.getReferenceday();
            Integer day, month, year;
            Calendar cal = Calendar.getInstance();

            ActionMessages errorMessages = valiDate(request, reportForm);
            if (errorMessages.size() > 0) {
                return mapping.getInputForward();
            }

            day = Integer.parseInt(datum.substring(8)); // parsing date from string 
            month = Integer.parseInt(datum.substring(5, 7));
            year = Integer.parseInt(datum.substring(0, 4));

            cal.set(Calendar.DATE, day);
            cal.set(Calendar.MONTH, month - 1);
            cal.set(Calendar.YEAR, year);

            cal.add(Calendar.DATE, howMuch);
            /* check if today is to be set (if howMuch == 0)or not */
            datum = howMuch == 0 ? simpleDateFormat.format(new java.util.Date()) : simpleDateFormat.format(cal.getTime());

            request.getSession().setAttribute("referenceday", datum);
            reportForm.setReferenceday(datum);

            if (coHelper.refreshOrders(request, reportForm, customerorderDAO, employeecontractDAO, suborderDAO) != true) {
                return mapping.findForward("error");
            } else {
                refreshTime = true;
            }
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("refreshOrders")) {
            // adjust the jsp with entries for Jira-Ticket-Keys, if the first customerorder in the dropdown-menu has Jira-Project-ID(s)
            if (coHelper.refreshOrders(request, reportForm, customerorderDAO, employeecontractDAO, suborderDAO) != true) {
                return mapping.findForward("error");
            } else {
                refreshTime = true;
            }
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("refreshSuborders")) {
            // refresh suborders to be displayed in the select menu
            String defaultSuborderIndexStr;
            if (request.getParameter("continue") == null) {
                defaultSuborderIndexStr = request.getParameter("defaultSuborderIndex");
            } else {
                defaultSuborderIndexStr = null;
            }
            if (soHelper.refreshSuborders(request, reportForm, suborderDAO, ticketDAO, employeecontractDAO, defaultSuborderIndexStr) != true) {
                return mapping.findForward("error");
            } else {
                Customerorder selectedOrder = customerorderDAO.getCustomerorderById(reportForm.getOrderId());
                boolean standardOrder = coHelper.isOrderStandard(selectedOrder);
                // selected order is a standard order => set daily working time als default time
                if (standardOrder) {
                    refreshTime = true;
                } else {
                    return mapping.findForward("success");
                }
            }
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("adjustBeginTime") || refreshTime) {

            // refresh orders to be displayed in the select menu
            if (coHelper.refreshOrders(request, reportForm, customerorderDAO, employeecontractDAO, suborderDAO) != true) {
                return mapping.findForward("error");
            }

            // refresh begin time to be displayed
            refreshTime = false;
            TimereportHelper th = new TimereportHelper();
            java.sql.Date selectedDate;

            try {
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern(GlobalConstants.DEFAULT_DATE_FORMAT);
                selectedDate = java.sql.Date.valueOf(LocalDate.parse(reportForm.getReferenceday(), dtf));
            } catch (DateTimeParseException e) {
                // error occured while parsing date - use current date instead
                selectedDate = java.sql.Date.valueOf(LocalDate.now());
            }
            request.getSession().setAttribute("referenceday", selectedDate);

            // search for adequate workingday and set status in session
            java.sql.Date currentDate = selectedDate;
            Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(currentDate, employeeContract.getId());

            boolean workingDayIsAvailable = false;
            if (workingday != null) {
                workingDayIsAvailable = true;
            }

            // workingday should only be available for today
            java.util.Date today = new java.util.Date();
            String todayString = simpleDateFormat.format(today);
            try {
                today = simpleDateFormat.parse(todayString);
            } catch (Exception e) {
                throw new RuntimeException("this should never happen...!");
            }
            if (!selectedDate.equals(today)) {
                workingDayIsAvailable = false;
            }
            request.getSession().setAttribute("workingDayIsAvailable", workingDayIsAvailable);

            Double dailyWorkingTime = employeeContract.getDailyWorkingTime();
            dailyWorkingTime *= 60;
            int dailyWorkingTimeMinutes = dailyWorkingTime.intValue();
            Customerorder selectedOrder = customerorderDAO.getCustomerorderById(reportForm.getOrderId());
            boolean standardOrder = coHelper.isOrderStandard(selectedOrder);

            if (workingDayIsAvailable) {
                // set the begin time as the end time of the latest existing timereport of current employee
                // for current day. If no other reports exist so far, set standard begin time (0800).
                int[] beginTime = th.determineBeginTimeToDisplay(employeeContract.getId(), timereportDAO, selectedDate, workingday);
                reportForm.setSelectedHourBegin(beginTime[0]);
                reportForm.setSelectedMinuteBegin(beginTime[1]);
                // set end time in reportform
                today = new java.util.Date();
                SimpleDateFormat minuteFormat = new SimpleDateFormat("mm");
                SimpleDateFormat hourFormat = new SimpleDateFormat("HH");
                int hour = new Integer(hourFormat.format(today));
                int minute = new Integer(minuteFormat.format(today));
                minute = minute / 5 * 5;

                todayString = simpleDateFormat.format(today);
                try {
                    today = simpleDateFormat.parse(todayString);
                } catch (Exception e) {
                    throw new RuntimeException("this should never happen...!");
                }
                if (standardOrder) {
                    int minutes = reportForm.getSelectedHourBegin() * 60 + reportForm.getSelectedMinuteBegin();
                    minutes += dailyWorkingTimeMinutes;
                    int hours = minutes / 60;
                    minutes = minutes % 60;
                    reportForm.setSelectedMinuteEnd(minutes);
                    reportForm.setSelectedHourEnd(hours);
                } else if ((beginTime[0] < hour || beginTime[0] == hour && beginTime[1] < minute) && selectedDate.equals(today)) {
                    reportForm.setSelectedMinuteEnd(minute);
                    reportForm.setSelectedHourEnd(hour);
                } else {
                    reportForm.setSelectedMinuteEnd(beginTime[1]);
                    reportForm.setSelectedHourEnd(beginTime[0]);
                }
                TimereportHelper.refreshHours(reportForm);
            } else {
                if (standardOrder) {

                    int hours = dailyWorkingTimeMinutes / 60;
                    int minutes = dailyWorkingTimeMinutes % 60;

                    if (minutes % GlobalConstants.MINUTE_INCREMENT != 0) {
                        if (minutes % GlobalConstants.MINUTE_INCREMENT > 2.5) {
                            minutes += 5 - minutes % GlobalConstants.MINUTE_INCREMENT;
                        } else if (minutes % GlobalConstants.MINUTE_INCREMENT < 2.5) {
                            minutes -= minutes % GlobalConstants.MINUTE_INCREMENT;
                        }
                    }

                    reportForm.setSelectedHourDuration(hours);
                    reportForm.setSelectedMinuteDuration(minutes);
                }
            }

            return mapping.findForward("success");
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("adjustSuborderSignChanged")) {

            // refresh suborder sign/description select menus
            soHelper.adjustSuborderSignChanged(request.getSession(), reportForm, suborderDAO);
            Suborder suborder = suborderDAO.getSuborderById(reportForm.getSuborderSignId());
            request.getSession().setAttribute("currentSuborderSign", suborder.getSign());
            setSubOrder(suborder, request, reportForm);

            return mapping.findForward("success");
        }

        if (request.getParameter("task") != null && request.getParameter("task").equalsIgnoreCase("updateSortOfReport")) {
            // updates the sort of report
            request.getSession().setAttribute("report", reportForm.getSortOfReport());
            return mapping.findForward("success");
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("refreshHours")) {
            // refreshes the hours displayed after a change of duration period
            TimereportHelper.refreshHours(reportForm);
            return mapping.findForward("success");
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("refreshPeriod")) {
            // refreshes the duration period after a change of begin/end times
            ActionMessages periodErrors = new ActionMessages();
            if (TimereportHelper.refreshPeriod(request, reportForm) != true) {
                saveErrors(request, periodErrors);
            }
            return mapping.findForward("success");
        }


        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("save") ||
                request.getParameter("trId") != null) {


            // 'main' task - prepare everything to store the report.
            // I.e., copy properties from the form into the timereport before saving.
            Employeecontract employeecontract = employeecontractDAO.getEmployeeContractById(reportForm.getEmployeeContractId());
            double hours = TimereportHelper.calculateTime(reportForm);
            long trId = -1;
            Timereport tr = null;
            if (request.getSession().getAttribute("trId") != null) {
                trId = Long.parseLong(request.getSession().getAttribute("trId").toString());
                tr = timereportDAO.getTimereportById(trId);
                previousTicket = tr.getTicket();
                previousDurationhours = tr.getDurationhours();
                previousDurationminutes = tr.getDurationminutes();
            } else if (request.getParameter("trId") != null) {
                // edited report from daily overview
                trId = Long.parseLong(request.getParameter("trId"));
                tr = timereportDAO.getTimereportById(trId);
                previousTicket = tr.getTicket();
                previousDurationhours = tr.getDurationhours();
                previousDurationminutes = tr.getDurationminutes();
            } else {
                // new report
                tr = new Timereport();
                tr.setStatus(GlobalConstants.TIMEREPORT_STATUS_OPEN);
            }

            //TODO: kann warscheinlich nach oben in selben if statement
            if (oauthVerifier1 != null) {
                restoreFormData(request, reportForm);
            }

            ActionMessages errors = getErrors(request);
            if (errors == null) {
                errors = new ActionMessages();
            }
            ActionMessages errorMessages = validateFormData(request, reportForm, trId, employeecontract.getId(), hours, errors);
            if (errorMessages.size() > 0) {
                return mapping.getInputForward();
            }

            Date theDate = Date.valueOf(reportForm.getReferenceday());
            tr.setTaskdescription(reportForm.getComment());
            tr.setEmployeecontract(employeecontract);
            tr.setTraining(reportForm.getTraining());

            // TICKET-Functionality
            // check if chosen order has at least one ProjectID - otherwise, no ticket functionality is needed
            if ((Boolean) request.getSession().getAttribute("projectIDExists")) {

                String jiraAccessToken = employeeContract.getEmployee().getJira_oauthtoken();

                // if JIRA is accessed for the first time or the access token is invalid
                if ((jiraAccessToken == null && request.getParameter("oauth_verifier") == null) ||
                        (jiraAccessToken != null && AtlassianOAuthClient.isValidAccessToken(jiraAccessToken) == false)) {

                    saveStaticFormDataToSession(request, reportForm);

                    // STEP 1: get a request token from JIRA and redirect user to JIRA login page
                    AtlassianOAuthClient.getRequestTokenAndSetRedirectToJira(response, GlobalConstants.SALAT_URL + "do/StoreDailyReport?task=save");
                    return null;
                } else {
                    AtlassianOAuthClient.setAccessToken(jiraAccessToken);
                }

                // STEP 2: JIRA returned a verifier code. Now swap the request token and the verifier with access token
                String oauthVerifier = request.getParameter("oauth_verifier");
                if (oauthVerifier != null) {
                    if (oauthVerifier.equals("denied")) {
                        addErrorAtTheBottom(request, errors, new ActionMessage("oauth.error.denied"));
                        return mapping.getInputForward();
                    } else {
                        String accessToken = AtlassianOAuthClient.swapRequestTokenForAccessToken(oauthVerifier, employeeDAO, employeeContract.getEmployee());
                        if (accessToken == null) return mapping.findForward("error");
                    }
                }

                //if new Jira-Ticket-Key has been entered, check if it really is not known in Salat yet (for this Order). If so, create an instance of Ticket for it. 
                if (reportForm.getNewJiraTicketKey() != null && !reportForm.getNewJiraTicketKey().equals("")) {

                    List<Ticket> knownTickets = ticketDAO.getTicketsByCustomerorderID(reportForm.getOrderId());
                    boolean newTicketKnownForSuborder = false;

                    for (Ticket t : knownTickets) {
                        if (t.getSuborder().getId() == reportForm.getSuborderSignId()) {
                            if (t.getJiraTicketKey().equals(reportForm.getNewJiraTicketKey())) {
                                newTicketKnownForSuborder = true;
                                break;
                            }
                        }
                    }

                    if (!newTicketKnownForSuborder) {
                        Ticket t = new Ticket();
                        t.setJiraTicketKey(reportForm.getNewJiraTicketKey());
                        t.setSuborder(suborderDAO.getSuborderById(reportForm.getSuborderSignId()));
                        //set fromDate an untilDate to the corresponding dates from suborder
                        t.setFromDate(t.getSuborder().getFromDate());
                        t.setUntilDate(t.getSuborder().getUntilDate());
                        ticketDAO.save(t);
                        tr.setTicket(t);
                        tr.setSuborder(t.getSuborder());
                    } else {
                        //Ticket already exists, only set it and its Suborder into the timereport
                        Ticket ticket = ticketDAO.getTicketByJiraTicketKeyAndDate(reportForm.getNewJiraTicketKey(), theDate);
                        if (ticket != null) {
                            tr.setTicket(ticket);
                            tr.setSuborder(tr.getTicket().getSuborder());
                        } else {
                            errors.add("newJiraTicketKeyErr", new ActionMessage("form.timereport.error.jira.noTicketWithKeyAndDate"));
                        }
                    }
                    // if no new key has been entered, set ticket corresponding to chosen key from dropdown-menu into the timereport
                    // has to have a value other than -1, or validateFormData would have returned at least one error before.
                } else {
                    Ticket ticket = ticketDAO.getTicketByJiraTicketKeyAndDate(reportForm.getJiraTicketKey(), theDate);
                    if (ticket != null) {
                        tr.setTicket(ticket);
                        tr.setSuborder(tr.getTicket().getSuborder());
                    } else {
                        errors.add("noTicketWithKeyAndDate", new ActionMessage("form.timereport.error.jira.noTicketWithKeyAndDate"));
                    }
                }

                saveErrors(request, errors);
                if (errorMessages.size() > 0) {
                    return mapping.getInputForward();
                }
            }

            // currently every timereport has status 'w'
            if (!reportForm.getSortOfReport().equals("W")) {
                Double durationMinutes = employeecontract.getDailyWorkingTime() % 1 * 60;
                tr.setDurationhours(employeecontract.getDailyWorkingTime().intValue());
                tr.setDurationminutes(durationMinutes.intValue());
            } else {
                tr.setDurationhours(new Integer(reportForm.getSelectedHourDuration()));
                tr.setDurationminutes(new Integer(reportForm.getSelectedMinuteDuration()));
            }

            tr.setSortofreport("W");

            if (tr.getReferenceday() == null ||
                    tr.getReferenceday().getRefdate() == null ||
                    !tr.getReferenceday().getRefdate().equals(theDate)) {
                // if timereport is new
                Referenceday rd = referencedayDAO.getReferencedayByDate(theDate);
                if (rd == null) {
                    // new referenceday to be added in database
                    referencedayDAO.addReferenceday(theDate);
                    rd = referencedayDAO.getReferencedayByDate(theDate);
                }
                tr.setReferenceday(rd);
            }

            // set employee order
            Employeeorder employeeorder = (Employeeorder) request.getSession().getAttribute("saveEmployeeOrder");
            tr.setEmployeeorder(employeeorder);
            request.getSession().removeAttribute("saveEmployeeOrder");

            if (reportForm.getSortOfReport().equals("W")) {
                tr.setCosts(reportForm.getCosts());
                //only set Suborder to the Suborder chosen by the employee, if no Jira-Project-ID is given for the order.
                if (!(Boolean) request.getSession().getAttribute("projectIDExists")) {
                    tr.setSuborder(suborderDAO.getSuborderById(reportForm.getSuborderSignId()));
                }
            } else {
                // 'special' reports: set suborder in timereport to null.	
                // no need to check for Jira-Project-ID here since special reports have no Jira-Equivalent
                tr.setSuborder(null);
                tr.setCosts(0.0);
            }

            List<Timereport> timereports = timereportDAO.getTimereportsByDateAndEmployeeContractId(tr.getEmployeecontract().getId(), tr.getReferenceday().getRefdate());
            boolean reportFoundInList = false;
            if (timereports != null && !timereports.isEmpty()) {
                Iterator<Timereport> it = timereports.iterator();
                while (it.hasNext()) {
                    Timereport timereport = it.next();
                    if (timereport.getId() == tr.getId()) {
                        reportFoundInList = true;
                        break;
                    }
                }
            }
            if (!reportFoundInList) {
                if (timereports.isEmpty()) {
                    tr.setSequencenumber(1);
                } else {
                    int lastindex = timereports.size() - 1;
                    tr.setSequencenumber(timereports.get(lastindex).getSequencenumber() + 1);
                }
            }

            java.util.Date releaseDate = employeecontract.getReportReleaseDate();
            if (releaseDate == null) {
                releaseDate = employeecontract.getValidFrom();
            }
            java.util.Date acceptanceDate = employeecontract.getReportAcceptanceDate();
            if (acceptanceDate == null) {
                acceptanceDate = employeecontract.getValidFrom();
            }
            java.util.Date refDate = tr.getReferenceday().getRefdate();

            boolean firstday = false;
            if (!releaseDate.after(employeecontract.getValidFrom()) &&
                    !refDate.after(employeecontract.getValidFrom())) {
                firstday = true;
            }

            if (!refDate.after(releaseDate) && !firstday) {
                tr.setStatus(GlobalConstants.TIMEREPORT_STATUS_COMMITED);
            }
            if (!refDate.after(acceptanceDate) && !firstday) {
                tr.setStatus(GlobalConstants.TIMEREPORT_STATUS_CLOSED);
            }

            Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
            int numberOfLaborDays = reportForm.getNumberOfSerialDays();

            // is the timereport a booking for vacation?
            if (tr.getSuborder() != null
                    && tr.getSuborder().getCustomerorder().getSign().equals(GlobalConstants.CUSTOMERORDER_SIGN_VACATION)
                    && !tr.getSuborder().getSign().equals(GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
                //fill VacationView with data
                Employeeorder vacationOrder = employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(employeecontract.getId(), tr.getSuborder().getId(), refDate);
                VacationViewer vacationView = new VacationViewer(employeecontract);
                vacationView.setSuborderSign(vacationOrder.getSuborder().getSign());
                if (vacationOrder.getDebithours() != null) {
                    vacationView.setBudget(vacationOrder.getDebithours());
                } else { //should not happen since debit hours of yearly vacation order is generated automatically when the order is created
                    vacationOrder.setDebithours(vacationOrder.getEmployeecontract().getVacationEntitlement() * vacationOrder.getEmployeecontract().getDailyWorkingTime());
                    vacationView.setBudget(vacationOrder.getDebithours());
                }
                List<Timereport> timereps = timereportDAO.getTimereportsBySuborderIdAndEmployeeContractId(vacationOrder.getSuborder().getId(), employeecontract.getId());
                for (Timereport timereport : timereps) {
                    if (tr.getId() != timereport.getId()) {
                        vacationView.addVacationMinutes(60 * timereport.getDurationhours());
                        vacationView.addVacationMinutes(timereport.getDurationminutes());
                    }
                }

                numberOfLaborDays = Math.max(numberOfLaborDays, 1);
                vacationView.addVacationMinutes(60 * numberOfLaborDays * tr.getDurationhours());
                vacationView.addVacationMinutes(numberOfLaborDays * tr.getDurationminutes());

                //check if current timereport/serial reports would overrun vacation budget of corresponding year of suborder
                if (vacationView.getExtended()) {
                    request.getSession().setAttribute("vacationBudgetOverrun", true);
                    return mapping.findForward("showDaily");
                }
            }
            request.getSession().setAttribute("vacationBudgetOverrun", false);
            TimereportHelper th = new TimereportHelper();
            if (numberOfLaborDays > 1) {
                if (tr.getId() != 0) {
                    timereportDAO.deleteTimereportById(tr.getId());
                }
                Date startDate = tr.getReferenceday().getRefdate();

                List<java.util.Date> dates = th.getDatesForTimePeriod(startDate, numberOfLaborDays, publicholidayDAO);
                for (java.util.Date date : dates) {
                    java.sql.Date sqlDate = new java.sql.Date(date.getTime());
                    Referenceday rd = referencedayDAO.getReferencedayByDate(sqlDate);
                    if (rd == null) {
                        // new referenceday to be added in database
                        referencedayDAO.addReferenceday(sqlDate);
                        rd = referencedayDAO.getReferencedayByDate(sqlDate);
                    }
                    Timereport serialReport = tr.getTwin();
                    serialReport.setReferenceday(rd);
                    timereportDAO.save(serialReport, loginEmployee, true);
                }
            } else {
                timereportDAO.save(tr, loginEmployee, true);
            }


            // WORKLOG-Functionality
            if (!projectIDs.isEmpty()) {

                // check if a Salat-Worklog already exists for this timereport
                Worklog salatWorklog = worklogDAO.getWorklogByTimereportID(tr.getId());
                String customerorderSign = projectIDs.get(0).getJiraProjectID();
                String jiraKey = customerorderSign + "-";

                if (reportForm.getJiraTicketKey().equals("-1")) {
                    jiraKey = jiraKey + reportForm.getNewJiraTicketKey();
                } else {
                    jiraKey = jiraKey + reportForm.getJiraTicketKey();
                }
                //if no worklog exists for this timereport, create a new worklog
                if (salatWorklog == null) {

                    int[] responseCreateWorklog = jcHelper.createWorklog(tr, jiraKey);
                    if (responseCreateWorklog[0] != 200) {
                        request.getSession().setAttribute("createWorklogFailed", responseCreateWorklog[0]);
                        try {
                            JiraSalatHelper.saveFailedWorklog(worklogMemoryDAO, timereportDAO, tr, jiraKey, 0, GlobalConstants.CREATE_WORKLOG);
                        } catch (Exception e) {
                            addErrorAtTheBottom(request, errors, new ActionMessage("form.general.error.jiraworklog.createerror", responseCreateWorklog[0]));
                        }
                    } else {
                        // create a salat-worklog
                        salatWorklog = new Worklog();
                        salatWorklog.setJiraWorklogID(responseCreateWorklog[1]);
                        salatWorklog.setJiraTicketKey(jiraKey);
                        salatWorklog.setTimereport(tr);
                        salatWorklog.setType("created");
                        salatWorklog.setUpdatecounter(0);
                    }

                    // check if Jira-Ticket-Key has been changed for this save
                } else if (previousTicket != null && tr.getTicket().getId() != previousTicket.getId()) {
                    // need to delete the existing Jira-Worklog for the previous Ticket, create a new one for the new Ticket, and adjust the Salat-Worklog
                    int responseDeleteWorklog = jcHelper.deleteWorklog(salatWorklog.getJiraWorklogID(), jiraKey);
                    if (responseDeleteWorklog != 200) {
                        addErrorAtTheBottom(request, errors, new ActionMessage("form.general.error.jiraworklog.deleteerror", responseDeleteWorklog));
                        try {
                            JiraSalatHelper.saveFailedWorklog(worklogMemoryDAO, timereportDAO, tr, "", salatWorklog.getJiraWorklogID(), GlobalConstants.DELETE_WORKLOG);
                            JiraSalatHelper.saveFailedWorklog(worklogMemoryDAO, timereportDAO, tr, jiraKey, 0, GlobalConstants.CREATE_WORKLOG);
                        } catch (Exception e) {
                            addErrorAtTheBottom(request, errors, new ActionMessage("form.general.error.worklogmemoryfailed"));
                        }
                    } else {
                        int[] responseCreateWorklog = jcHelper.createWorklog(tr, jiraKey);
                        if (responseCreateWorklog[0] != 200) {
                            addErrorAtTheBottom(request, errors, new ActionMessage("form.general.error.jiraworklog.createerror", responseCreateWorklog[0]));
                            try {
                                JiraSalatHelper.saveFailedWorklog(worklogMemoryDAO, timereportDAO, tr, jiraKey, 0, GlobalConstants.CREATE_WORKLOG);
                            } catch (Exception e) {
                                addErrorAtTheBottom(request, errors, new ActionMessage("form.general.error.worklogmemoryfailed"));
                            }
                        } else {
                            salatWorklog.setJiraWorklogID(responseCreateWorklog[1]);
                            salatWorklog.setType("updated");
                            salatWorklog.setUpdatecounter(salatWorklog.getUpdatecounter() + 1);
                        }
                    }
                    // check if Durationhours and/or Durationminutes have been adjusted for this save.
                } else if (tr.getDurationhours() != previousDurationhours || tr.getDurationminutes() != previousDurationminutes || !tr.getStatus().equals(previousComment)) {
                    // if so, update the existing Jira-Worklog and the Salat-Worklog
                    int responseUpdateWorklog = jcHelper.updateWorklog(tr, jiraKey, salatWorklog.getJiraWorklogID());
                    //if Worklog not found/has been deleted - try to create a new one
                    if (responseUpdateWorklog == 404) {
                        int[] create_status = jcHelper.createWorklog(tr, jiraKey);
                        if (create_status[0] != 200) {
                            addErrorAtTheBottom(request, errors, new ActionMessage("form.general.error.jiraworklog.updateerror", responseUpdateWorklog));
                            try {
                                JiraSalatHelper.saveFailedWorklog(worklogMemoryDAO, timereportDAO, tr, jiraKey, 0, GlobalConstants.CREATE_WORKLOG);
                            } catch (Exception e) {
                                addErrorAtTheBottom(request, errors, new ActionMessage("form.general.error.worklogmemoryfailed"));
                            }
                        } else {
                            salatWorklog.setJiraWorklogID(create_status[1]);
                            salatWorklog.setType("updated");
                            salatWorklog.setUpdatecounter(salatWorklog.getUpdatecounter() + 1);
                        }
                    } else if (responseUpdateWorklog != 200) {
                        addErrorAtTheBottom(request, errors, new ActionMessage("form.general.error.jiraworklog.updateerror", responseUpdateWorklog));
                        try {
                            JiraSalatHelper.saveFailedWorklog(worklogMemoryDAO, timereportDAO, tr, jiraKey, salatWorklog.getJiraWorklogID(), GlobalConstants.UPDATE_WORKLOG);
                        } catch (Exception e) {
                            addErrorAtTheBottom(request, errors, new ActionMessage("form.general.error.worklogmemoryfailed"));
                        }
                    }

                    if (tr.getDurationhours() != previousDurationhours || tr.getDurationminutes() != previousDurationminutes) {
                        salatWorklog.setType("updated");
                        salatWorklog.setUpdatecounter(salatWorklog.getUpdatecounter() + 1);
                    }
                }
                if (salatWorklog != null) {
                    worklogDAO.save(salatWorklog);
                }
                if (errorMessages.size() > 0) {
                    return mapping.getInputForward();
                }
            }


            if (tr.getStatus().equalsIgnoreCase(GlobalConstants.TIMEREPORT_STATUS_CLOSED) && loginEmployee.getStatus().equalsIgnoreCase("adm")) {
                // recompute overtimeStatic and store it in employeecontract
                double otStatic = th.calculateOvertime(employeecontract.getValidFrom(), employeecontract.getReportAcceptanceDate(),
                        employeecontract, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO, true);
                employeecontract.setOvertimeStatic(otStatic / 60.0);
                employeecontractDAO.save(employeecontract, loginEmployee);
            }

            request.getSession().setAttribute("currentDay", DateUtils.getDayString(theDate));
            request.getSession().setAttribute("currentMonth", DateUtils.getMonthShortString(theDate));
            request.getSession().setAttribute("currentYear", DateUtils.getYearString(theDate));
            List<Timereport> reports;
            if (request.getSession().getAttribute("trId") != null) {
                request.getSession().removeAttribute("trId");
            }
            Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(tr.getReferenceday().getRefdate(), employeecontract.getId());

            if (request.getParameter("continue") == null || !Boolean.parseBoolean(request.getParameter("continue"))) {
                // set new ShowDailyReportForm with saved filter settings
                ShowDailyReportForm showDailyReportForm = new ShowDailyReportForm();

                java.sql.Date referenceDate;
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern(GlobalConstants.DEFAULT_DATE_FORMAT);
                try {
                    referenceDate = java.sql.Date.valueOf(LocalDate.parse(reportForm.getReferenceday(), dtf));
                } catch (DateTimeParseException e) {
                    // error occured while parsing date - use current date instead
                    referenceDate = java.sql.Date.valueOf(LocalDate.now());
                }
                showDailyReportForm.setDay(DateUtils.getDayString(referenceDate));
                showDailyReportForm.setMonth(DateUtils.getMonthShortString(referenceDate));
                showDailyReportForm.setYear(DateUtils.getYearString(referenceDate));

                showDailyReportForm.setStartdate(showDailyReportForm.getYear() + "-" + DateUtils.getMonthMMStringFromShortstring(showDailyReportForm.getMonth()) + "-" + showDailyReportForm.getDay());
                if (request.getSession().getAttribute("lastLastMonth") != null) {
                    showDailyReportForm.setLastday((String) request.getSession().getAttribute("lastLastDay"));
                    showDailyReportForm.setLastmonth((String) request.getSession().getAttribute("lastLastMonth"));
                    showDailyReportForm.setLastyear((String) request.getSession().getAttribute("lastLastYear"));
                } else {
                    try {
                        referenceDate = java.sql.Date.valueOf(LocalDate.parse(reportForm.getReferenceday(), dtf));
                    } catch (DateTimeParseException e) {
                        // error occured while parsing date - use current date instead
                        referenceDate = java.sql.Date.valueOf(LocalDate.now());
                    }
                    showDailyReportForm.setLastday(DateUtils.getDayString(referenceDate));
                    showDailyReportForm.setLastmonth(DateUtils.getMonthShortString(referenceDate));
                    showDailyReportForm.setLastyear(DateUtils.getYearString(referenceDate));
                }
                showDailyReportForm.setEnddate(showDailyReportForm.getLastyear() + "-" + DateUtils.getMonthMMStringFromShortstring(showDailyReportForm.getLastmonth()) + "-"
                        + showDailyReportForm.getLastday());
                request.getSession().removeAttribute("lastCurrentDay");
                request.getSession().removeAttribute("lastCurrentMonth");
                request.getSession().removeAttribute("lastCurrentYear");
                request.getSession().removeAttribute("lastLastDay");
                request.getSession().removeAttribute("lastLastMonth");
                request.getSession().removeAttribute("lastLastYear");

                if (request.getSession().getAttribute("lastView") != null) {
                    showDailyReportForm.setView((String) request.getSession().getAttribute("lastView"));
                } else {
                    showDailyReportForm.setView(GlobalConstants.VIEW_DAILY);
                }
                request.getSession().removeAttribute("lastView");
                showDailyReportForm.setOrder((String) request.getSession().getAttribute("lastOrder"));
                if (request.getSession().getAttribute("lastSuborderId") != null) {
                    showDailyReportForm.setSuborderId((Long) request.getSession().getAttribute("lastSuborderId"));
                } else {
                    showDailyReportForm.setSuborderId(-1l);
                }
                if (request.getSession().getAttribute("lastEmployeeContractId") != null) {
                    showDailyReportForm.setEmployeeContractId((Long) request.getSession().getAttribute("lastEmployeeContractId"));
                } else {
                    showDailyReportForm.setEmployeeContractId(loginEmployee.getId());
                }
                request.getSession().removeAttribute("lastSuborderId");
                request.getSession().removeAttribute("lastOrder");
                request.getSession().removeAttribute("lastEmployeeContractId");

                // get updated list of timereports from DB
                refreshTimereports(
                        request,
                        showDailyReportForm,
                        customerorderDAO,
                        timereportDAO,
                        employeecontractDAO,
                        suborderDAO,
                        employeeorderDAO
                );
                reports = (List<Timereport>) request.getSession().getAttribute("timereports");
                request.getSession().setAttribute("suborderFilerId", showDailyReportForm.getSuborderId());

                request.getSession().setAttribute("labortime", th.calculateLaborTime(reports));
                request.getSession().setAttribute("maxlabortime", th.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
                request.getSession().setAttribute("dailycosts", th.calculateDailyCosts(timereports));
                request.getSession().setAttribute("quittingtime", th.calculateQuittingTime(workingday, request, "quittingtime"));

                //calculate Working Day End
                request.getSession().setAttribute("workingDayEnds", th.calculateQuittingTime(workingday, request, "workingDayEnds"));

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
                    showDailyReportForm.setSelectedWorkHourBegin(workingday.getStarttimehour());
                    showDailyReportForm.setSelectedWorkMinuteBegin(workingday.getStarttimeminute());
                    showDailyReportForm.setSelectedBreakHour(workingday.getBreakhours());
                    showDailyReportForm.setSelectedBreakMinute(workingday.getBreakminutes());
                } else {
                    // don't show break time, quitting time and working day ends on
                    // the showdailyreport.jsp
                    request.getSession().setAttribute("visibleworkingday", false);
                    showDailyReportForm.setSelectedWorkHourBegin(0);
                    showDailyReportForm.setSelectedWorkMinuteBegin(0);
                    showDailyReportForm.setSelectedBreakHour(0);
                    showDailyReportForm.setSelectedBreakMinute(0);
                }
                // refresh overtime and vacation
                refreshVacationAndOvertime(request, employeecontract, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO);
                return mapping.findForward("showDaily");

            } else { // Continue = true

                java.sql.Date selectedDate = getSelectedDateFromRequest(request);

                //deleting comment, costs and days of serialBookings in the addDailyReport-Form
                reportForm.setComment("");
                reportForm.setCosts(0.0);
                reportForm.setNumberOfSerialDays(0);

                if (workingday != null) {
                    int[] beginTime = th.determineBeginTimeToDisplay(employeecontract.getId(), timereportDAO, selectedDate, workingday);
                    reportForm.setSelectedHourBegin(beginTime[0]);
                    reportForm.setSelectedMinuteBegin(beginTime[1]);
                    reportForm.setNumberOfSerialDays(0);
                    java.util.Date today = new java.util.Date();
                    SimpleDateFormat minuteFormat = new SimpleDateFormat("mm");
                    SimpleDateFormat hourFormat = new SimpleDateFormat("HH");
                    int hour = new Integer(hourFormat.format(today));
                    int minute = new Integer(minuteFormat.format(today));
                    minute = minute / 5 * 5;

                    String todayString = simpleDateFormat.format(today);
                    try {
                        today = simpleDateFormat.parse(todayString);
                    } catch (Exception e) {
                        throw new RuntimeException("this should never happen...!");
                    }

                    if ((beginTime[0] < hour || beginTime[0] == hour && beginTime[1] < minute) && selectedDate.equals(today)) {
                        reportForm.setSelectedMinuteEnd(minute);
                        reportForm.setSelectedHourEnd(hour);
                    } else {
                        reportForm.setSelectedMinuteEnd(beginTime[1]);
                        reportForm.setSelectedHourEnd(beginTime[0]);
                    }
                    TimereportHelper.refreshHours(reportForm);

                } else {
                    reportForm.setSelectedHourDuration(0);
                    reportForm.setSelectedMinuteDuration(0);
                }

                // set orders and suborders
                List<Customerorder> orders = customerorderDAO.getCustomerordersWithValidEmployeeOrders(employeecontract.getId(), selectedDate);

                // set order
                request.getSession().setAttribute("orders", orders);

                List<Suborder> theSuborders = new ArrayList<Suborder>();
                if (orders != null && !orders.isEmpty()) {
                    long orderId = reportForm.getOrderId();
                    if (orderId == 0) {
                        orderId = orders.get(0).getId();
                    }
                    theSuborders = suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(employeecontract.getId(), orderId, selectedDate);
                    if (theSuborders == null || theSuborders.isEmpty()) {
                        request.setAttribute("errorMessage", "Orders/suborders inconsistent for employee - please call system administrator.");
                        return mapping.findForward("error");
                    }
                } else {
                    request.setAttribute("errorMessage", "no orders found for employee - please call system administrator.");
                    return mapping.findForward("error");
                }
                // set suborders
                request.getSession().setAttribute("suborders", theSuborders);

                return mapping.findForward("addDaily");
            }

        }
        if (request.getParameter("task") != null && request.getParameter("task").equals("back")) {
            // go back
            request.getSession().removeAttribute("trId");
            reportForm.reset(mapping, request);
            return mapping.findForward("cancel");
        }
        if (request.getParameter("task") != null && request.getParameter("task").equals("reset")) {
            // reset form
            doResetActions(mapping, request, reportForm);
            return mapping.getInputForward();
        }

        return mapping.findForward("error");
    }

    private void saveStaticFormDataToSession(HttpServletRequest request, AddDailyReportForm reportForm) {

        request.getSession().setAttribute("numberOfSerialDays", reportForm.getNumberOfSerialDays());
        request.getSession().setAttribute("newJiraTicketKey", reportForm.getNewJiraTicketKey());
        request.getSession().setAttribute("costs", reportForm.getCosts());
        request.getSession().setAttribute("comment", reportForm.getComment());
    }

    private void restoreFormData(HttpServletRequest request, AddDailyReportForm reportForm) {

        SimpleDateFormat sdFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
        java.util.Date date = (java.util.Date) request.getSession().getAttribute("referenceday");
        if (date == null) reportForm.setReferenceday(sdFormat.format(new java.util.Date()));
        else {
            String referenceday = sdFormat.format(date);
            if (referenceday != null) reportForm.setReferenceday(referenceday);
        }
        Integer numberOfSerialDays = (Integer) request.getSession().getAttribute("numberOfSerialDays");
        if (numberOfSerialDays != null) reportForm.setNumberOfSerialDays(numberOfSerialDays);
        String jiraTicketKey = (String) request.getSession().getAttribute("jiraTicketKey");
        if (jiraTicketKey != null) reportForm.setJiraTicketKey(jiraTicketKey);
        String newJiraTicketKey = (String) request.getSession().getAttribute("newJiraTicketKey");
        if (newJiraTicketKey != null) reportForm.setNewJiraTicketKey(newJiraTicketKey);
        Double hours = (Double) request.getSession().getAttribute("hourDuration");
        if (hours != null) reportForm.setHours(hours);
        TimereportHelper.refreshHours(reportForm);
        Double costs = (Double) request.getSession().getAttribute("costs");
        if (costs != null) reportForm.setCosts(costs);
        String comment = (String) request.getSession().getAttribute("comment");
        if (comment != null) reportForm.setComment(comment);
    }

    private Employeecontract getEmployeeContractAndSetSessionVars(
            ActionMapping mapping, HttpServletRequest request) {
        Employeecontract ec;
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");

        if (request.getSession().getAttribute("currentEmployeeContract") != null) {
            Employeecontract currentEmployeeContract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
            ec = currentEmployeeContract;
            request.getSession().setAttribute("currentEmployee", ec.getEmployee().getName());
            request.getSession().setAttribute("currentEmployeeId", ec.getEmployee().getId());
        } else {
            ec = loginEmployeeContract;
            request.getSession().setAttribute("currentEmployee", loginEmployee.getName());
            request.getSession().setAttribute("currentEmployeeId", loginEmployee.getId());
            request.getSession().setAttribute("currentEmployeeContract", loginEmployeeContract);
        }

        return ec;
    }

    private void setSubOrder(@Nonnull Suborder suborder, HttpServletRequest request, AddDailyReportForm reportForm) {

        // adjust the jsp with entries for Jira-Ticket-Keys for the chosen suborder
        JiraSalatHelper.setJiraTicketKeysForSuborder(request, ticketDAO, suborder.getId());

        // if selected Suborder is Overtime Compensation, delete the previously automatically set daily working time
        // also make sure that overtimeCompensation is set in the session so that the duration-dropdown-menu will be disabled
        if (suborder != null && suborder.getSign().equalsIgnoreCase(GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
            reportForm.setSelectedHourDuration(0);
            reportForm.setSelectedMinuteDuration(0);
            if (request.getSession().getAttribute("overtimeCompensation") == null || request.getSession().getAttribute("overtimeCompensation")
                    != GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION) {
                request.getSession().setAttribute("overtimeCompensation", GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION);
            }

        }

        // if selected Suborder has a default-flag for projectbased training, set training in the form to true, so that the training-box in the jsp is checked
        if (Boolean.TRUE.equals(suborder.getTrainingFlag())) {
            reportForm.setTraining(true);
        }
    }

    /**
     * resets the 'add report' form to default values
     *
     * @param mapping
     * @param request
     * @param reportForm
     */
    private void doResetActions(ActionMapping mapping, HttpServletRequest request, AddDailyReportForm reportForm) {
        reportForm.reset(mapping, request);

        //reset the current employee Session Variables
        Employeecontract ec = null;
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");

        ec = loginEmployeeContract;

        List<Employeecontract> employeecontracts = employeecontractDAO.getVisibleEmployeeContractsForEmployee(loginEmployee);
        request.getSession().setAttribute("employeecontracts", employeecontracts);

        request.getSession().setAttribute("currentEmployee", loginEmployee.getName());
        request.getSession().setAttribute("currentEmployeeId", loginEmployee.getId());
        request.getSession().setAttribute("currentEmployeeContract", loginEmployeeContract);

        String dateString = reportForm.getReferenceday();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
        java.util.Date date;
        try {
            date = simpleDateFormat.parse(dateString);
        } catch (Exception e) {
            throw new RuntimeException("error while parsing date");
        }

        List<Customerorder> orders = customerorderDAO.getCustomerordersWithValidEmployeeOrders(ec.getId(), date);
        List<Suborder> suborders = null;
        request.getSession().setAttribute("orders", orders);
        request.getSession().setAttribute("report", "W");

        //reset first order and corresponding suborders
        if (orders != null && orders.size() > 0) {
            reportForm.setOrder(orders.get(0).getSign());
            reportForm.setOrderId(orders.get(0).getId());
            // prepare second collection of suborders sorted by description
            suborders = suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(ec.getId(), orders.get(0).getId(), date);
            request.getSession().setAttribute("suborders", suborders);
        } else {
            request.setAttribute("errorMessage", "No orders found for employee - please call system administrator.");
            mapping.findForward("error");
        }

        if (request.getSession().getAttribute("trId") != null) {
            //get the Timereport object
            long trId = Long.parseLong(request.getSession().getAttribute("trId").toString());
            Timereport tr = timereportDAO.getTimereportById(trId);

            //reset the rest
            reportForm.setReferenceday(tr.getReferenceday().getRefdate().toString());
            if (tr.getTicket() != null) {
                if (!suborders.isEmpty()) {
                    // adjust the jsp with entries for Jira-Ticket-Keys, if the first customerorder in the dropdown-menu has Jira-Project-ID(s)
                    JiraSalatHelper.setJiraTicketKeysForSuborder(request, ticketDAO, suborders.get(0).getId());
                }
                //reportForm.setJiraTicketKey(tr.getTicket().getJiraTicketKey());
                request.getSession().setAttribute("jiraTicketKey", tr.getTicket().getJiraTicketKey());
                // set isEdit = true into the session, so order/suborder menu will be disabled
                request.getSession().setAttribute("isEdit", true);
            } else {
                request.getSession().setAttribute("isEdit", false);
            }
            reportForm.setSelectedHourDuration(tr.getDurationhours());
            reportForm.setSelectedMinuteDuration(tr.getDurationminutes());
            reportForm.setCosts(tr.getCosts());
            reportForm.setTraining(tr.getTraining());
            reportForm.setComment(tr.getTaskdescription());
        } else {
            reportForm.reset();
        }

    }

    private ActionMessages valiDate(HttpServletRequest request, AddDailyReportForm reportForm) {
        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }

        String dateString = reportForm.getReferenceday().trim();

        int minus = 0;
        for (int i = 0; i < dateString.length(); i++) {
            if (dateString.charAt(i) == '-') {
                minus++;
            }
        }
        if (dateString.length() != 10 || minus != 2) {
            errors.add("referenceday", new ActionMessage("form.timereport.error.date.wrongformat"));
        }

        saveErrors(request, errors);
        return errors;
    }

    /**
     * validates the form data (syntax and logic)
     *
     * @param request
     * @param reportForm
     * @param trId:      > 0 for edited report, -1 for new report
     * @param ecId
     * @param hours
     * @return
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

        boolean dateError = DateUtils.validateDate(dateString);
        if (dateError) {
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
            if (reportForm.getSortOfReport().equals("W")) {
                if (begin >= end) {
                    errors.add("selectedHourBegin", new ActionMessage("form.timereport.error.endbeforebegin"));
                }
            }
        }
        // check if report types for one day are unique and if there is no time overlap with other work reports
        List<Timereport> dailyReports = timereportDAO.getTimereportsByDateAndEmployeeContractId(ecId, theDate);
        if (dailyReports != null && dailyReports.size() > 0) {
            for (Object element : dailyReports) {
                Timereport tr = (Timereport) element;
                if (tr.getId() != trId) { // do not check report against itself in case of edit
                    // uniqueness of types
                    // actually not checked - e.g., combination of sickness and work on ONE day should be valid
                    // but: vacation or sickness MUST occur only once per day
                    if (!reportForm.getSortOfReport().equals("W") && !tr.getSortofreport().equals("W")) {
                        errors.add("sortOfReport", new ActionMessage("form.timereport.error.sortofreport.special.alreadyexisting"));
                        break;
                    }
                }
            }
        }

        // check if orders/suborders are filled in case of 'W' report
        if (reportForm.getSortOfReport().equals("W")) {
            if (reportForm.getOrderId() <= 0) {
                errors.add("orderId", new ActionMessage("form.timereport.error.orderid.empty"));
            }
            if (reportForm.getSuborderSignId() <= 0) {
                errors.add("suborderIdDescription", new ActionMessage("form.timereport.error.suborderid.empty"));
            }
        }

        // if sort of report is not 'W' reports are only allowed for workdays
        // e.g., vacation cannot be set on a Sunday
        if (!reportForm.getSortOfReport().equals("W")) {
            boolean valid = !DateUtils.isSatOrSun(theDate);

            // checks for public holidays
            if (valid) {
                String publicHoliday = publicholidayDAO.getPublicHoliday(theDate);
                if (publicHoliday != null && publicHoliday.length() > 0) {
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
                        valid = false;
                        errors.add("sortOfReport", new ActionMessage("form.timereport.error.sortofreport.othersexisting"));
                    }
                }
            }

        }

        // check if for Timereports whose Orders have a Jira-Project-ID, a Jira-Ticket-Key has been chosen or newly entered.
        if ((Boolean) request.getSession().getAttribute("projectIDExists")) {
            if (reportForm.getJiraTicketKey().equals("-1") && reportForm.getNewJiraTicketKey().equals("")) {
                errors.add("noKeySelected", new ActionMessage("form.timereport.error.noJiraTicketKey"));
            } else if (!reportForm.getJiraTicketKey().equals("-1") && !reportForm.getNewJiraTicketKey().equals("")) {
                errors.add("noKeySelected", new ActionMessage("form.timereport.error.twoJiraTickets"));
            } else {
                // if a new Jira-Ticket-Key has been entered, check with Jira if Ticket exists
                List<ProjectID> projectIDs = customerorderDAO.getCustomerorderById(reportForm.getOrderId()).getProjectIDs();
                if (projectIDs.size() > 0 && reportForm.getJiraTicketKey().equals("-1")) {
                    String customerorderSign = projectIDs.get(0).getJiraProjectID();
                    String jiraKey = customerorderSign + "-" + reportForm.getNewJiraTicketKey();
                    int responseCheckJiraTicket = jcHelper.checkJiraTicketID(jiraKey);
                    if (responseCheckJiraTicket != 200) {
                        if (responseCheckJiraTicket == 404) {
                            errors.add("nonexistentKey", new ActionMessage("form.timereport.error.jiraTicketNotExists"));
                        } else if (responseCheckJiraTicket == 500) {
                            //No error returned so that we can save the Report even if Jira is unreachable
                        }
                    }
                }
            }

            //also check if Duration is > 0, because Jira-Worklog doesnt accept 0 Duration
            if (hours == 0) {
                errors.add("selectedDuration", new ActionMessage("form.timereport.error.hours.unset"));
                saveErrors(request, errors);
                return errors;
            }
        }

        // if an existing Jira-Ticket-Key has been chosen, check if the employer has the corresponding suborder as employeeorder
        if (!reportForm.getJiraTicketKey().equals("-1")) {
            Ticket t = null;
            if (reportForm.getJiraTicketKey().equals("-1")) {
                t = ticketDAO.getTicketByJiraTicketKeyAndDate(reportForm.getNewJiraTicketKey(), theDate);
            } else {
                t = ticketDAO.getTicketByJiraTicketKeyAndDate(reportForm.getJiraTicketKey(), theDate);
            }
            if (t == null) {
                errors.add("noTicketWithKeyAndDate", new ActionMessage("form.timereport.error.jira.noTicketWithKeyAndDate"));
            } else {
                Employeeorder eo = employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(ecId, t.getSuborder().getId(), theDate);
                if (eo == null) {
                    errors.add("noEmployeeOrderForJiraTicketKey", new ActionMessage("form.timereport.error.jira.noEmployeeOrder"));
                }
            }
        }

        // check costs format
        if (reportForm.getSortOfReport().equals("W")) {
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
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
        java.util.Date refDate = null;
        Date releaseDate = employeecontract.getReportReleaseDate();
        if (releaseDate == null) {
            releaseDate = employeecontract.getValidFrom();
        }
        Date acceptanceDate = employeecontract.getReportAcceptanceDate();
        if (acceptanceDate == null) {
            acceptanceDate = employeecontract.getValidFrom();
        }
        try {
            refDate = simpleDateFormat.parse(reportForm.getReferenceday());
        } catch (Exception e) {
            throw new RuntimeException("date cannot be parsed (yyyy-MM-dd)");
        }

        // check, if refDate is first day
        boolean firstday = false;
        if (!releaseDate.after(employeecontract.getValidFrom()) &&
                !refDate.after(employeecontract.getValidFrom())) {
            firstday = true;
        }

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
                TimereportHelper th = new TimereportHelper();
                List<java.util.Date> dates = th.getDatesForTimePeriod(theDate, numberOfLaborDays, publicholidayDAO);
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

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }
}
