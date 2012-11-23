package org.tb.web.action;

import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Referenceday;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;
import org.tb.bdom.Workingday;
import org.tb.helper.CustomerorderHelper;
import org.tb.helper.SuborderHelper;
import org.tb.helper.TimereportHelper;
import org.tb.helper.VacationViewer;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.OvertimeDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.ReferencedayDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.VacationDAO;
import org.tb.persistence.WorkingdayDAO;
import org.tb.util.DateUtils;
import org.tb.web.form.AddDailyReportForm;
import org.tb.web.form.ShowDailyReportForm;

/**
 * Action class for a timereport to be stored permanently.
 * 
 * @author oda
 *
 */
public class StoreDailyReportAction extends DailyReportAction {
    
    private EmployeeDAO employeeDAO;
    private EmployeecontractDAO employeecontractDAO;
    private SuborderDAO suborderDAO;
    private CustomerorderDAO customerorderDAO;
    private TimereportDAO timereportDAO;
    private ReferencedayDAO referencedayDAO;
    private PublicholidayDAO publicholidayDAO;
    private VacationDAO vacationDAO;
    private WorkingdayDAO workingdayDAO;
    private EmployeeorderDAO employeeorderDAO;
    private OvertimeDAO overtimeDAO;
    
    public void setOvertimeDAO(OvertimeDAO overtimeDAO) {
        this.overtimeDAO = overtimeDAO;
    }
    
    public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
        this.employeeorderDAO = employeeorderDAO;
    }
    
    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
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
    
    public void setVacationDAO(VacationDAO vacationDAO) {
        this.vacationDAO = vacationDAO;
    }
    
    public void setWorkingdayDAO(WorkingdayDAO workingdayDAO) {
        this.workingdayDAO = workingdayDAO;
    }
    
    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
        
        // check if special tasks initiated from the form or the daily display need to be carried out...
        AddDailyReportForm reportForm = (AddDailyReportForm)form;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
        boolean refreshTime = false;
        
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
            
            CustomerorderHelper ch = new CustomerorderHelper();
            if (ch.refreshOrders(mapping, request, reportForm,
                    customerorderDAO, employeeDAO, employeecontractDAO, suborderDAO) != true) {
                return mapping.findForward("error");
            } else {
                //return mapping.findForward("success");
                refreshTime = true;
            }
            
        }
        
        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("refreshOrders")) {
            // refresh orders to be displayed in the select menu
            CustomerorderHelper ch = new CustomerorderHelper();
            if (ch.refreshOrders(mapping, request, reportForm,
                    customerorderDAO, employeeDAO, employeecontractDAO, suborderDAO) != true) {
                return mapping.findForward("error");
            } else {
                //return mapping.findForward("success");
                refreshTime = true;
            }
        }
        
        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("refreshSuborders")) {
            // refresh suborders to be displayed in the select menu
            SuborderHelper sh = new SuborderHelper();
            if (sh.refreshSuborders(mapping, request, reportForm,
                    suborderDAO, employeecontractDAO) != true) {
                return mapping.findForward("error");
            } else {
                Customerorder selectedOrder = customerorderDAO.getCustomerorderById(reportForm.getOrderId());
                
                boolean standardOrder = false;
                if (selectedOrder != null &&
                        (selectedOrder.getSign().equalsIgnoreCase(GlobalConstants.CUSTOMERORDER_SIGN_VACATION) ||
                                selectedOrder.getSign().equalsIgnoreCase(GlobalConstants.CUSTOMERORDER_SIGN_EXTRA_VACATION) ||
                                selectedOrder.getSign().equalsIgnoreCase(GlobalConstants.CUSTOMERORDER_SIGN_ILL) ||
                        selectedOrder.getSign().equalsIgnoreCase(GlobalConstants.CUSTOMERORDER_SIGN_REMAINING_VACATION))) {
                    // selected order is a standard order => set daily working time als default time	
                    standardOrder = true;
                }
                
                if (standardOrder) {
                    refreshTime = true;
                } else {
                    return mapping.findForward("success");
                }
            }
        }
        
        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("adjustBeginTime") || refreshTime) {
            
            // refresh orders to be displayed in the select menu
            CustomerorderHelper ch = new CustomerorderHelper();
            if (ch.refreshOrders(mapping, request, reportForm,
                    customerorderDAO, employeeDAO, employeecontractDAO, suborderDAO) != true) {
                return mapping.findForward("error");
            }
            
            // refresh begin time to be displayed
            refreshTime = false;
            Employee loginEmployee = (Employee)request.getSession().getAttribute("loginEmployee");
            Employeecontract loginEmployeeContract = (Employeecontract)request.getSession().getAttribute("loginEmployeeContract");
            Employeecontract ec = null;
            
            if (request.getSession().getAttribute("currentEmployeeContract") != null) {
                Employeecontract currentEmployeeContract = (Employeecontract)request.getSession().getAttribute("currentEmployeeContract");
                ec = currentEmployeeContract;
                request.getSession().setAttribute("currentEmployee", ec.getEmployee().getName());
                request.getSession().setAttribute("currentEmployeeId", ec.getEmployee().getId());
            } else {
                ec = loginEmployeeContract;
                request.getSession().setAttribute("currentEmployee", loginEmployee.getName());
                request.getSession().setAttribute("currentEmployeeId", loginEmployee.getId());
                request.getSession().setAttribute("currentEmployeeContract", loginEmployeeContract);
            }
            
            if (ec == null) {
                request.setAttribute("errorMessage",
                        "No employee contract found for employee - please call system administrator.");
                return mapping.findForward("error");
            }
            
            TimereportHelper th = new TimereportHelper();
            
            java.util.Date selectedDate;
            try {
                selectedDate = simpleDateFormat.parse(reportForm.getReferenceday());
            } catch (ParseException e) {
                // error occured while parsing date - use current date instead
                selectedDate = new java.util.Date();
            }
            request.getSession().setAttribute("referenceday", selectedDate);
            
            // search for adequate workingday and set status in session
            java.sql.Date currentDate = DateUtils.getSqlDate(selectedDate);
            Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(currentDate, ec.getId());
            
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
            
            Double dailyWorkingTime = ec.getDailyWorkingTime();
            dailyWorkingTime *= 60;
            int dailyWorkingTimeMinutes = dailyWorkingTime.intValue();
            Customerorder selectedOrder = customerorderDAO.getCustomerorderById(reportForm.getOrderId());
            boolean standardOrder = false;
            if (selectedOrder != null &&
                    (selectedOrder.getSign().equalsIgnoreCase(GlobalConstants.CUSTOMERORDER_SIGN_VACATION) ||
                            selectedOrder.getSign().equalsIgnoreCase(GlobalConstants.CUSTOMERORDER_SIGN_EXTRA_VACATION) ||
                            selectedOrder.getSign().equalsIgnoreCase(GlobalConstants.CUSTOMERORDER_SIGN_ILL) ||
                    selectedOrder.getSign().equalsIgnoreCase(GlobalConstants.CUSTOMERORDER_SIGN_REMAINING_VACATION))) {
                // selected order is a standard order => set daily working time als default time	
                standardOrder = true;
            }
            
            if (workingDayIsAvailable) {
                // set the begin time as the end time of the latest existing timereport of current employee
                // for current day. If no other reports exist so far, set standard begin time (0800).
                int[] beginTime = th.determineBeginTimeToDisplay(ec.getId(), timereportDAO, selectedDate, workingday);
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
                // working day is not available
                //					reportForm.setSelectedHourBegin(0);
                //					reportForm.setSelectedHourDuration(0);
                //					reportForm.setSelectedHourEnd(0);
                //					reportForm.setSelectedMinuteBegin(0);
                //					reportForm.setSelectedMinuteDuration(0);
                //					reportForm.setSelectedMinuteEnd(0);
                
                if (standardOrder) {
                    
                    int hours = dailyWorkingTimeMinutes / 60;
                    int minutes = dailyWorkingTimeMinutes % 60;
                    
                    //						// clean possible truncation errors
                    //						if (minutes % GlobalConstants.MINUTE_INCREMENT == 1) minutes--;
                    //						if (minutes % GlobalConstants.MINUTE_INCREMENT == GlobalConstants.MINUTE_INCREMENT-1) minutes++;
                    
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
        
        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("adjustSuborderSignChanged")) {
            // refresh suborder sign/description select menus
            SuborderHelper sh = new SuborderHelper();
            sh.adjustSuborderSignChanged(request, reportForm, suborderDAO);
            
            // if selected Suborder is Overtime Compensation, delete the previously automatically set daily working time
            Suborder suborder = suborderDAO.getSuborderById(reportForm.getSuborderSignId());
            if (suborder != null &&
                    suborder.getSign().equalsIgnoreCase(GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
                reportForm.setSelectedHourDuration(0);
                reportForm.setSelectedMinuteDuration(0);
            }
            
            // if selected Suborder has a default-flag for projectbased training, set training in the form to true, so that the training-box in the jsp is checked
            if (suborder.getTrainingFlag()) {
                reportForm.setTraining(true);
            }
            
            return mapping.findForward("success");
        }
        
        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("adjustSuborderDescriptionChanged")) {
            // refresh suborder sign/description select menus
            SuborderHelper sh = new SuborderHelper();
            sh.adjustSuborderDescriptionChanged(request, reportForm, suborderDAO);
            return mapping.findForward("success");
        }
        
        if (request.getParameter("task") != null &&
                request.getParameter("task").equalsIgnoreCase("updateSortOfReport")) {
            // updates the sort of report
            request.getSession().setAttribute("report", reportForm.getSortOfReport());
            return mapping.findForward("success");
        }
        
        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("refreshHours")) {
            // refreshes the hours displayed after a change of duration period
            TimereportHelper.refreshHours(reportForm);
            return mapping.findForward("success");
        }
        
        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("refreshPeriod")) {
            // refreshes the duration period after a change of begin/end times
            ActionMessages periodErrors = new ActionMessages();
            if (TimereportHelper.refreshPeriod(request, periodErrors, reportForm) != true) {
                saveErrors(request, periodErrors);
            }
            return mapping.findForward("success");
        }
        
        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("save") ||
                request.getParameter("trId") != null) {
            
            // 'main' task - prepare everything to store the report.
            // I.e., copy properties from the form into the timereport before saving.
            Employeecontract ec = employeecontractDAO.getEmployeeContractById(reportForm.getEmployeeContractId());
            double hours = TimereportHelper.calculateTime(reportForm);
            long trId = -1;
            Timereport tr = null;
            if (request.getSession().getAttribute("trId") != null) {
                trId = Long.parseLong(request.getSession().getAttribute("trId").toString());
                tr = timereportDAO.getTimereportById(trId);
            } else if (request.getParameter("trId") != null) {
                // edited report from daily overview
                trId = Long.parseLong(request.getParameter("trId"));
                tr = timereportDAO.getTimereportById(trId);
            } else {
                // new report
                tr = new Timereport();
                tr.setStatus(GlobalConstants.TIMEREPORT_STATUS_OPEN);
            }
            
            ActionMessages errorMessages = validateFormData(request, reportForm, trId, ec.getId(), hours);
            if (errorMessages.size() > 0) {
                return mapping.getInputForward();
            }
            Date theDate = Date.valueOf(reportForm.getReferenceday());
            tr.setTaskdescription(reportForm.getComment());
            tr.setEmployeecontract(ec);
            tr.setTraining(reportForm.getTraining());
            
            // currently every timereport has status 'w'
            if (!reportForm.getSortOfReport().equals("W")) {
                Double durationMinutes = ec.getDailyWorkingTime() % 1 * 60;
                tr.setDurationhours(ec.getDailyWorkingTime().intValue());
                tr.setDurationminutes(durationMinutes.intValue());
            } else {
                tr.setDurationhours(new Integer(reportForm.getSelectedHourDuration()));
                tr.setDurationminutes(new Integer(reportForm.getSelectedMinuteDuration()));
            }
            
            tr.setSortofreport(/*reportForm.getSortOfReport()*/"W");
            
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
            Employeeorder employeeorder = (Employeeorder)request.getSession().getAttribute("saveEmployeeOrder");
            tr.setEmployeeorder(employeeorder);
            request.getSession().removeAttribute("saveEmployeeOrder");
            
            if (reportForm.getSortOfReport().equals("W")) {
                tr.setCosts(reportForm.getCosts());
                tr.setSuborder(suborderDAO.getSuborderById(reportForm.getSuborderSignId()));
                
            } else { // TODO
                // 'special' reports: set suborder in timereport to null.				
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
            
            java.util.Date releaseDate = ec.getReportReleaseDate();
            java.util.Date acceptanceDate = ec.getReportAcceptanceDate();
            java.util.Date refDate = tr.getReferenceday().getRefdate();
            
            boolean firstday = false;
            if (!releaseDate.after(ec.getValidFrom()) &&
                    !refDate.after(ec.getValidFrom())) {
                firstday = true;
            }
            
            if (!refDate.after(releaseDate) && !firstday) {
                tr.setStatus(GlobalConstants.TIMEREPORT_STATUS_COMMITED);
            }
            if (!refDate.after(acceptanceDate) && !firstday) {
                tr.setStatus(GlobalConstants.TIMEREPORT_STATUS_CLOSED);
            }
            
            Employee loginEmployee = (Employee)request.getSession().getAttribute("loginEmployee");
            int numberOfLaborDays = reportForm.getNumberOfSerialDays();
            
            // is the timereport a booking for vacation?
            if (tr.getSuborder() != null
                    && tr.getSuborder().getCustomerorder().getSign().equals(GlobalConstants.CUSTOMERORDER_SIGN_VACATION)
                    && !tr.getSuborder().getSign().equals(GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
                //fill VacationView with data
                java.sql.Date today = new java.sql.Date(new java.util.Date().getTime());
                Employeeorder vacationOrder = employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(ec.getId(), tr.getSuborder().getId(), today);
                VacationViewer vacationView = new VacationViewer(ec);
                vacationView.setSuborderSign(vacationOrder.getSuborder().getSign());
                if (vacationOrder.getDebithours() != null) {
                    vacationView.setBudget(vacationOrder.getDebithours());
                } else { //should not happen since debit hours of yearly vacation order is generated automatically when the order is created
                    vacationOrder.setDebithours(vacationOrder.getEmployeecontract().getVacationEntitlement() * vacationOrder.getEmployeecontract().getDailyWorkingTime());
                    vacationView.setBudget(vacationOrder.getDebithours());
                }
                List<Timereport> timereps = timereportDAO.getTimereportsBySuborderIdAndEmployeeContractId(vacationOrder.getSuborder().getId(), ec.getId());
                for (Timereport timereport : timereps) {
                    if (tr.getId() != timereport.getId()) {
                        vacationView.addVacationHours(timereport.getDurationhours());
                        vacationView.addVacationMinutes(timereport.getDurationminutes());
                    }
                }
                if (numberOfLaborDays > 1) {
                    for (int i = 0; i < numberOfLaborDays; i++) {
                        vacationView.addVacationHours(tr.getDurationhours());
                        vacationView.addVacationMinutes(tr.getDurationminutes());
                    }
                } else {
                    vacationView.addVacationHours(tr.getDurationhours());
                    vacationView.addVacationMinutes(tr.getDurationminutes());
                }
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
            
            if (tr.getStatus().equalsIgnoreCase(GlobalConstants.TIMEREPORT_STATUS_CLOSED) && loginEmployee.getStatus().equalsIgnoreCase("adm")) {
                // recompute overtimeStatic and store it in employeecontract
                int[] otStatic = th.calculateOvertime(ec.getValidFrom(), ec.getReportAcceptanceDate(),
                        ec, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO, true);
                ec.setOvertimeStatic(otStatic[0] + otStatic[1] / 60.0);
                employeecontractDAO.save(ec, loginEmployee);
            }
            
            request.getSession().setAttribute("currentDay", DateUtils.getDayString(theDate));
            request.getSession().setAttribute("currentMonth", DateUtils.getMonthShortString(theDate));
            request.getSession().setAttribute("currentYear", DateUtils.getYearString(theDate));
            List<Timereport> reports;
            if (request.getSession().getAttribute("trId") != null) {
                request.getSession().removeAttribute("trId");
            }
            Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(tr.getReferenceday().getRefdate(), ec.getId());
            if (request.getParameter("continue") == null || !Boolean.parseBoolean(request.getParameter("continue"))) {
                // set new ShowDailyReportForm with saved filter settings
                ShowDailyReportForm showDailyReportForm = new ShowDailyReportForm();
                if (request.getSession().getAttribute("lastCurrentMonth") != null) {
                    showDailyReportForm.setDay((String)request.getSession().getAttribute("lastCurrentDay"));
                    showDailyReportForm.setMonth((String)request.getSession().getAttribute("lastCurrentMonth"));
                    showDailyReportForm.setYear((String)request.getSession().getAttribute("lastCurrentYear"));
                } else {
                    java.util.Date referenceDate;
                    try {
                        referenceDate = simpleDateFormat.parse(reportForm.getReferenceday());
                    } catch (ParseException e) {
                        // error occured while parsing date - use current date instead
                        referenceDate = new java.util.Date();
                    }
                    showDailyReportForm.setDay(DateUtils.getDayString(referenceDate));
                    showDailyReportForm.setMonth(DateUtils.getMonthShortString(referenceDate));
                    showDailyReportForm.setYear(DateUtils.getYearString(referenceDate));
                }
                showDailyReportForm.setStartdate(showDailyReportForm.getYear() + "-" + DateUtils.getMonthMMStringFromShortstring(showDailyReportForm.getMonth()) + "-" + showDailyReportForm.getDay());
                if (request.getSession().getAttribute("lastLastMonth") != null) {
                    showDailyReportForm.setLastday((String)request.getSession().getAttribute("lastLastDay"));
                    showDailyReportForm.setLastmonth((String)request.getSession().getAttribute("lastLastMonth"));
                    showDailyReportForm.setLastyear((String)request.getSession().getAttribute("lastLastYear"));
                } else {
                    java.util.Date referenceDate;
                    try {
                        referenceDate = simpleDateFormat.parse(reportForm.getReferenceday());
                    } catch (ParseException e) {
                        // error occured while parsing date - use current date instead
                        referenceDate = new java.util.Date();
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
                    showDailyReportForm.setView((String)request.getSession().getAttribute("lastView"));
                } else {
                    showDailyReportForm.setView(GlobalConstants.VIEW_DAILY);
                }
                request.getSession().removeAttribute("lastView");
                showDailyReportForm.setOrder((String)request.getSession().getAttribute("lastOrder"));
                if (request.getSession().getAttribute("lastSuborderId") != null) {
                    showDailyReportForm.setSuborderId((Long)request.getSession().getAttribute("lastSuborderId"));
                } else {
                    showDailyReportForm.setSuborderId(-1l);
                }
                if (request.getSession().getAttribute("lastEmployeeContractId") != null) {
                    showDailyReportForm.setEmployeeContractId((Long)request.getSession().getAttribute("lastEmployeeContractId"));
                } else {
                    showDailyReportForm.setEmployeeContractId(loginEmployee.getId());
                }
                request.getSession().removeAttribute("lastSuborderId");
                request.getSession().removeAttribute("lastOrder");
                request.getSession().removeAttribute("lastEmployeeContractId");
                
                // get updated list of timereports from DB
                refreshTimereports(mapping,
                        request,
                        showDailyReportForm,
                        customerorderDAO,
                        timereportDAO,
                        employeecontractDAO,
                        suborderDAO,
                        employeeorderDAO,
                        publicholidayDAO,
                        overtimeDAO,
                        vacationDAO,
                        employeeDAO);
                reports = (List<Timereport>)request.getSession().getAttribute("timereports");
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
                    // don´t show break time, quitting time and working day ends on
                    // the showdailyreport.jsp
                    request.getSession().setAttribute("visibleworkingday", false);
                    showDailyReportForm.setSelectedWorkHourBegin(0);
                    showDailyReportForm.setSelectedWorkMinuteBegin(0);
                    showDailyReportForm.setSelectedBreakHour(0);
                    showDailyReportForm.setSelectedBreakMinute(0);
                }
                // refresh overtime and vacation
                refreshVacationAndOvertime(request, ec, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO);
                return mapping.findForward("showDaily");
            } else {
                
                java.util.Date selectedDate = getSelectedDateFromRequest(request);
                
                //deleting comment, costs and days of serialBookings in the addDailyReport-Form
                reportForm.setComment("");
                reportForm.setCosts(0.0);
                reportForm.setNumberOfSerialDays(0);
                
                if (workingday != null) {
                    int[] beginTime = th.determineBeginTimeToDisplay(ec.getId(), timereportDAO, selectedDate, workingday);
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
                List<Customerorder> orders = customerorderDAO.getCustomerordersWithValidEmployeeOrders(ec.getId(), selectedDate);
                
                // set order
                request.getSession().setAttribute("orders", orders);
                
                List<Suborder> theSuborders = new ArrayList<Suborder>();
                if (orders != null && !orders.isEmpty()) {
                    long orderId = reportForm.getOrderId();
                    if (orderId == 0) {
                        orderId = orders.get(0).getId();
                    }
                    theSuborders =
                            suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(ec.getId(), orderId, selectedDate);
                    if (theSuborders == null || theSuborders.isEmpty()) {
                        request.setAttribute("errorMessage",
                                "Orders/suborders inconsistent for employee - please call system administrator.");
                        return mapping.findForward("error");
                    }
                } else {
                    request.setAttribute("errorMessage",
                            "no orders found for employee - please call system administrator.");
                    return mapping.findForward("error");
                }
                // set suborder
                request.getSession().setAttribute("suborders", theSuborders);
                
                return mapping.findForward("addDaily");
                
            }
            
        }
        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("back")) {
            // go back
            request.getSession().removeAttribute("trId");
            reportForm.reset(mapping, request);
            return mapping.findForward("cancel");
        }
        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("reset")) {
            // reset form
            doResetActions(mapping, request, reportForm);
            return mapping.getInputForward();
        }
        
        return mapping.findForward("error");
        
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
        
        Employeecontract ec = null;
        Employee loginEmployee = (Employee)request.getSession().getAttribute("loginEmployee");
        Employeecontract loginEmployeeContract = (Employeecontract)request.getSession().getAttribute("loginEmployeeContract");
        
        ec = loginEmployeeContract;
        
        List<Employeecontract> employeecontracts = employeecontractDAO.getVisibleEmployeeContractsOrderedByEmployeeSign();
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
        
        //		List<Customerorder> orders = customerorderDAO.getCustomerordersByEmployeeContractId(ec.getId());
        List<Customerorder> orders = customerorderDAO.getCustomerordersWithValidEmployeeOrders(ec.getId(), date);
        
        request.getSession().setAttribute("orders", orders);
        
        request.getSession().setAttribute("report", "W");
        
        // init form with first order and corresponding suborders
        if (orders != null && orders.size() > 0) {
            reportForm.setOrder(orders.get(0).getSign());
            reportForm.setOrderId(orders.get(0).getId());
            
            // prepare second collection of suborders sorted by description
            //			List<Suborder> theSuborders = suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderId(ec.getId(), orders.get(0).getId());
            List<Suborder> theSuborders = suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(ec.getId(), orders.get(0).getId(), date);
            //			List<Suborder> subordersByDescription = new ArrayList<Suborder>();
            //			subordersByDescription.addAll(theSuborders);
            //			Collections.sort(subordersByDescription, new SubOrderByDescriptionComparator());
            request.getSession().setAttribute("suborders", theSuborders);
            //			request.getSession().setAttribute("subordersByDescription", subordersByDescription);
        } else {
            request.setAttribute("errorMessage",
                    "No orders found for employee - please call system administrator.");
            mapping.findForward("error");
        }
        request.getSession().removeAttribute("trId");
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
     * @param theDate
     * @param trId: > 0 for edited report, -1 for new report
     * @param ecId
     * @param hours
     * @return
     */
    private ActionMessages validateFormData(HttpServletRequest request,
            AddDailyReportForm reportForm,
            long trId,
            long ecId,
            double hours) {
        
        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }
        
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
        
        Boolean workingDayIsAvailable = (Boolean)request.getSession().getAttribute("workingDayIsAvailable");
        
        if (workingDayIsAvailable) {
            // end time must be later than begin time
            int begin = reportForm.getSelectedHourBegin() * 100
                    + reportForm.getSelectedMinuteBegin();
            int end = reportForm.getSelectedHourEnd() * 100
                    + reportForm.getSelectedMinuteEnd();
            if (reportForm.getSortOfReport().equals("W")) {
                if (begin >= end) {
                    errors.add("selectedHourBegin", new ActionMessage(
                            "form.timereport.error.endbeforebegin"));
                }
            }
        }
        // check if report types for one day are unique and if there is no time overlap with other work reports
        List<Timereport> dailyReports =
                timereportDAO.getTimereportsByDateAndEmployeeContractId(ecId, theDate);
        if (dailyReports != null && dailyReports.size() > 0) {
            for (Object element : dailyReports) {
                Timereport tr = (Timereport)element;
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
            boolean valid = true;
            String dow = DateUtils.getDow(theDate);
            if (dow.equalsIgnoreCase("Sat") || dow.equalsIgnoreCase("Sun")) {
                valid = false;
            }
            
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
                    List<Timereport> allReports =
                            timereportDAO.getTimereportsByDateAndEmployeeContractId(ecId, theDate);
                    if (allReports.size() > 0) {
                        valid = false;
                        errors.add("sortOfReport", new ActionMessage("form.timereport.error.sortofreport.othersexisting"));
                    }
                }
            }
            
        }
        
        // check costs format
        if (reportForm.getSortOfReport().equals("W")) {
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
        Suborder suborder = suborderDAO.getSuborderById(reportForm.getSuborderSignId());
        Boolean commentnecessary = suborder.getCommentnecessary();
        if (commentnecessary && (reportForm.getComment() == null || reportForm.getComment().trim().equals(""))) {
            errors.add("comment", new ActionMessage("form.timereport.error.comment.necessary"));
        }
        
        // check date vs release status
        Employeecontract employeecontract = employeecontractDAO.getEmployeeContractById(ecId);
        Employeecontract loginEmployeecontract = (Employeecontract)request.getSession().getAttribute("loginEmployeeContract");
        Boolean authorized = (Boolean)request.getSession().getAttribute("employeeAuthorized");
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
        java.util.Date refDate = null;
        Date releaseDate = employeecontract.getReportReleaseDate();
        Date acceptanceDate = employeecontract.getReportAcceptanceDate();
        try {
            refDate = simpleDateFormat.parse(reportForm
                    .getReferenceday());
        } catch (Exception e) {
            throw new RuntimeException("date cannot be parsed (yyyy-MM-dd)");
        }
        
        // check, if refDate is first day
        boolean firstday = false;
        if (!employeecontract.getReportReleaseDate().after(employeecontract.getValidFrom()) &&
                !refDate.after(employeecontract.getValidFrom())) {
            firstday = true;
        }
        
        if (!loginEmployeecontract.getEmployee().getSign().equals("adm")) {
            if (authorized && loginEmployeecontract.getId() != ecId) {
                if (releaseDate.before(refDate) || firstday) {
                    errors.add("release", new ActionMessage(
                            "form.timereport.error.not.released"));
                }
            } else {
                if (!releaseDate.before(refDate) && !firstday) {
                    errors.add("release", new ActionMessage(
                            "form.timereport.error.released"));
                }
            }
            if (!refDate.after(acceptanceDate) && !firstday) {
                errors.add("release", new ActionMessage(
                        "form.timereport.error.accepted"));
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
}
