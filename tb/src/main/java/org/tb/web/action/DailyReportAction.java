package org.tb.web.action;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;
import org.tb.bdom.Workingday;
import org.tb.helper.AfterLogin;
import org.tb.helper.TimereportHelper;
import org.tb.helper.VacationViewer;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.OvertimeDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.WorkingdayDAO;
import org.tb.util.DateUtils;
import org.tb.util.OptionItem;
import org.tb.web.form.ShowDailyReportForm;
import org.tb.web.util.OvertimeString;

public abstract class DailyReportAction extends LoginRequiredAction {
    
	
	protected void addErrorAtTheBottom(HttpServletRequest request, ActionMessages errors, ActionMessage message) {
		errors.add("status", message);
		request.getSession().setAttribute("errors", true);
		saveErrors(request, errors);
	}
	
    /**
     * 
     * @param request
     * @return Returns the date associated the request. If parsing fails, the current date is returned.
     */
    protected Date getSelectedDateFromRequest(HttpServletRequest request) {
        String dayString = (String)request.getSession().getAttribute("currentDay");
        String monthString = (String)request.getSession().getAttribute("currentMonth");
        String yearString = (String)request.getSession().getAttribute("currentYear");
        
        Date date;
        try {
            TimereportHelper th = new TimereportHelper();
            date = th.getDateFormStrings(dayString, monthString, yearString, true);
        } catch (Exception e) {
            // if parsing fails, return current date
            date = new Date();
        }
        
        return date;
    }
    
    /**
     * Calculates the overtime and vaction and sets the attributes in the session.
     * @param request
     * @param selectedYear
     * @param employeecontract
     */
    protected void refreshVacationAndOvertime(HttpServletRequest request, Employeecontract employeecontract,
            EmployeeorderDAO employeeorderDAO, PublicholidayDAO publicholidayDAO, TimereportDAO timereportDAO, OvertimeDAO overtimeDAO) {
    	AfterLogin.handleOvertime(employeecontract, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO, request.getSession());
        
        // release
        request.getSession().setAttribute("releaseWarning", employeecontract.getReleaseWarning());
        request.getSession().setAttribute("acceptanceWarning", employeecontract.getAcceptanceWarning());
        
        String releaseDate = employeecontract.getReportReleaseDateString();
        String acceptanceDate = employeecontract.getReportAcceptanceDateString();
        
        request.getSession().setAttribute("releasedUntil", releaseDate);
        request.getSession().setAttribute("acceptedUntil", acceptanceDate);
    }
    
    /**
     * Refreshes the list of timereports and all session attributes, that depend on the list of timereports.
     * 
     * @param mapping
     * @param request
     * @param reportForm
     * @param customerorderDAO
     * @param timereportDAO
     * @param employeecontractDAO
     * @param suborderDAO
     * @param employeeorderDAO
     * @param publicholidayDAO
     * @param overtimeDAO
     * @param vacationDAO
     * @param employeeDAO
     * @return Returns true, if refreshing was succesful. 
     */
    protected boolean refreshTimereports(ActionMapping mapping,
            HttpServletRequest request, ShowDailyReportForm reportForm, CustomerorderDAO customerorderDAO,
            TimereportDAO timereportDAO, EmployeecontractDAO employeecontractDAO, SuborderDAO suborderDAO,
            EmployeeorderDAO employeeorderDAO, PublicholidayDAO publicholidayDAO, OvertimeDAO overtimeDAO) {
        
        //selected view and selected dates
        String selectedView = reportForm.getView();
        if (selectedView == null) {
            selectedView = GlobalConstants.VIEW_DAILY;
        }
        Date beginDate;
        Date endDate;
        
        /* make sure that the form is set in the http session, it could be a newly created object */
        request.getSession().setAttribute("showDailyReportForm", reportForm);
        
        try {
            TimereportHelper th = new TimereportHelper();
            
            if (selectedView.equals(GlobalConstants.VIEW_DAILY)) {
                request.getSession().setAttribute("view", GlobalConstants.VIEW_DAILY);
                
                beginDate = th.getDateFormStrings(reportForm.getDay(), reportForm.getMonth(), reportForm.getYear(), true);
                endDate = beginDate;
            } else if (selectedView.equals(GlobalConstants.VIEW_MONTHLY)) {
                request.getSession().setAttribute("view", GlobalConstants.VIEW_MONTHLY);
                beginDate = th.getDateFormStrings("1", reportForm.getMonth(), reportForm.getYear(), true);
                GregorianCalendar gc = new GregorianCalendar();
                gc.setTime(beginDate);
                int maxday = gc.getActualMaximum(Calendar.DAY_OF_MONTH);
                String maxDayString = "";
                if (maxday < 10) {
                    maxDayString += "0";
                }
                maxDayString += maxday;
                endDate = th.getDateFormStrings(maxDayString, reportForm.getMonth(), reportForm.getYear(), true);
            } else if (selectedView.equals(GlobalConstants.VIEW_CUSTOM)) {
                request.getSession().setAttribute("view", GlobalConstants.VIEW_CUSTOM);
                beginDate = th.getDateFormStrings(reportForm.getDay(), reportForm.getMonth(), reportForm.getYear(), true);
                if (reportForm.getLastday() == null || reportForm.getLastmonth() == null || reportForm.getLastyear() == null) {
                    reportForm.setLastday(reportForm.getDay());
                    reportForm.setLastmonth(reportForm.getMonth());
                    reportForm.setLastyear(reportForm.getYear());
                }
                endDate = th.getDateFormStrings(reportForm.getLastday(), reportForm.getLastmonth(), reportForm.getLastyear(), true);
            } else {
                throw new RuntimeException("no view type selected");
            }
            
        } catch (Exception e) {
            throw new RuntimeException("date cannot be parsed for form", e);
        }
        java.sql.Date beginSqlDate = new java.sql.Date(beginDate.getTime());
        java.sql.Date endSqlDate = new java.sql.Date(endDate.getTime());
        
        // test, if an order is select, the selected employee is not associated with
        long employeeContractId = reportForm.getEmployeeContractId();
        if (employeeContractId != 0 && employeeContractId != -1) {
            String selectedOrder = reportForm.getOrder();
            Customerorder order = customerorderDAO.getCustomerorderBySign(selectedOrder);
            List<Employeeorder> employeeOrders = null;
            if (order != null) {
                employeeOrders = employeeorderDAO.getEmployeeordersByOrderIdAndEmployeeContractId(order.getId(), employeeContractId);
            }
            if (employeeOrders == null || employeeOrders.isEmpty()) {
                reportForm.setOrder(GlobalConstants.ALL_ORDERS);
            }
        } 
        
        List<Timereport> timereports = new ArrayList<Timereport>();
        if (reportForm.getEmployeeContractId() == -1) {
            // consider timereports for all employees
            List<Customerorder> orders = customerorderDAO.getCustomerorders();
            request.getSession().setAttribute("orders", orders);
            
            request.getSession().setAttribute("overtimeDisabled", true);
            
            if (reportForm.getOrder() == null || reportForm.getOrder().equals(GlobalConstants.ALL_ORDERS)) {
                // get the timereports for specific date, all employees, all orders
                timereports = timereportDAO.getTimereportsByDates(beginSqlDate, endSqlDate);
                
            } else {
                Customerorder co = customerorderDAO.getCustomerorderBySign(reportForm.getOrder());
                long orderId = co.getId();
                request.getSession().setAttribute("suborders", co.getSuborders());
                
                Suborder suborder = suborderDAO.getSuborderById(reportForm.getSuborderId());
                if (suborder == null || suborder.getCustomerorder().getId() != orderId) {
                    reportForm.setSuborderId(-1l);
                }
                
                if (reportForm.getSuborderId() == 0 || reportForm.getSuborderId() == -1) {
                    // get the timereports for specific date, all employees, specific order
                    timereports = timereportDAO.getTimereportsByDatesAndCustomerOrderId(beginSqlDate, endSqlDate, orderId);
                } else {
                    timereports = timereportDAO.getTimereportsByDatesAndSuborderId(beginSqlDate, endSqlDate, reportForm.getSuborderId());
                }
            }
            
        } else {
            // consider timereports for specific employee
            Employeecontract ec = employeecontractDAO.getEmployeeContractById(employeeContractId);
            if (ec == null) {
                request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator.");
                return false;
            }
            
            request.getSession().setAttribute("overtimeDisabled", false);
            
            // also refresh orders/suborders to be displayed for specific employee 
            List<Customerorder> orders = customerorderDAO.getCustomerordersByEmployeeContractId(ec.getId());
            request.getSession().setAttribute("orders", orders);
            if (reportForm.getOrder() == null || reportForm.getOrder().equals(GlobalConstants.ALL_ORDERS)) {
                // get the timereports for specific date, specific employee, all orders
                timereports = timereportDAO.getTimereportsByDatesAndEmployeeContractId(ec.getId(), beginSqlDate, endSqlDate);
            } else {
                Customerorder co = customerorderDAO.getCustomerorderBySign(reportForm.getOrder());
                long orderId = co.getId();
                
                List<Suborder> suborders = co.getSuborders();
                if(reportForm.getShowOnlyValid()) {
                	Iterator<Suborder> iter = suborders.iterator();
                	while(iter.hasNext()) {
                		if(!iter.next().getCurrentlyValid()) {
                			iter.remove();
                		}
                	}
                }
                
                request.getSession().setAttribute("suborders", suborders);
                Suborder suborder = suborderDAO.getSuborderById(reportForm.getSuborderId());
                if (suborder == null || suborder.getCustomerorder().getId() != orderId) {
                    reportForm.setSuborderId(-1l);
                }
                // get the timereports for specific date, specific employee, specific order
                // fill up order-specific list with 'working' reports only...
                if (reportForm.getSuborderId() == 0 || reportForm.getSuborderId() == -1) {
                    timereports = timereportDAO.getTimereportsByDatesAndEmployeeContractIdAndCustomerOrderId(ec.getId(), beginSqlDate, endSqlDate, orderId);
                } else {
                    timereports = timereportDAO.getTimereportsByDatesAndEmployeeContractIdAndSuborderId(ec.getId(), beginSqlDate, endSqlDate, reportForm.getSuborderId());
                }
            }
            // refresh overtime and vacation
            if (reportForm.getEmployeeContractId() != -1) {
                refreshVacationAndOvertime(request, ec, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO);
            }
        }
        
        // set timereports in session
        if (request.getSession().getAttribute("timereportComparator") != null) {
            @SuppressWarnings("unchecked")
			Comparator<Timereport> comparator = (Comparator<Timereport>)request.getSession().getAttribute("timereportComparator");
            Collections.sort(timereports, comparator);
        }
        request.getSession().setAttribute("timereports", timereports);
        request.getSession().setAttribute("currentSuborderId", reportForm.getSuborderId());
        
        // refresh all relevant attributes
        if (reportForm.getEmployeeContractId() == -1) {
            request.getSession().setAttribute("currentEmployee", GlobalConstants.ALL_EMPLOYEES);
            request.getSession().setAttribute("currentEmployeeContract", null);
            request.getSession().setAttribute("currentEmployeeId", -1l);
        } else {
            Employeecontract employeecontract = employeecontractDAO.getEmployeeContractById(reportForm.getEmployeeContractId());
            request.getSession().setAttribute("currentEmployee", employeecontract.getEmployee().getName());
            request.getSession().setAttribute("currentEmployeeContract", employeecontract);
            request.getSession().setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
        }
        
        if (reportForm.getOrder() == null || reportForm.getOrder().equals("ALL ORDERS")) {
            request.getSession().setAttribute("currentOrder", GlobalConstants.ALL_ORDERS);
        } else {
            request.getSession().setAttribute("currentOrder", reportForm.getOrder());
        }
        request.getSession().setAttribute("currentDay", reportForm.getDay());
        request.getSession().setAttribute("currentMonth", reportForm.getMonth());
        request.getSession().setAttribute("currentYear", reportForm.getYear());
        request.getSession().setAttribute("startdate", reportForm.getStartdate());
        
        request.getSession().setAttribute("lastDay", reportForm.getLastday());
        request.getSession().setAttribute("lastMonth", reportForm.getLastmonth());
        request.getSession().setAttribute("lastYear", reportForm.getLastyear());
        request.getSession().setAttribute("enddate", reportForm.getEnddate());
        
        request.getSession().setAttribute("reportForm", reportForm);
        
        return true;
    }
    
    /**
     * Refreshes the workingday.
     * @param mapping
     * @param reportForm
     * @param request
     * @throws Exception
     */
    protected Workingday refreshWorkingday(ActionMapping mapping, ShowDailyReportForm reportForm, HttpServletRequest request, EmployeecontractDAO employeecontractDAO, WorkingdayDAO workingdayDAO)
            throws Exception {
        
        Employeecontract employeecontract = getEmployeeContractFromRequest(request, employeecontractDAO);
        if (employeecontract == null) {
            request.setAttribute("errorMessage",
                    "No employee contract found for employee - please call system administrator.");
            throw new Exception("No employee contract found for employee");
        }
        //new parameter
        Workingday workingday = getWorkingdayForReportformAndEmployeeContract(reportForm, employeecontract, workingdayDAO, false);
        
        // save values from the data base into form-bean, when working day != null
        if (workingday != null) {
            
            //show break time, quitting time and working day ends on the showdailyreport.jsp
            request.getSession().setAttribute("visibleworkingday", true);
            
            reportForm.setSelectedWorkHourBegin(workingday.getStarttimehour());
            reportForm.setSelectedWorkMinuteBegin(workingday.getStarttimeminute());
            reportForm.setSelectedBreakHour(workingday.getBreakhours());
            reportForm.setSelectedBreakMinute(workingday.getBreakminutes());
        }
        else {
            
            //don�t show break time, quitting time and working day ends on the showdailyreport.jsp
            request.getSession().setAttribute("visibleworkingday", false);
            
            reportForm.setSelectedWorkHourBegin(0);
            reportForm.setSelectedWorkMinuteBegin(0);
            reportForm.setSelectedBreakHour(0);
            reportForm.setSelectedBreakMinute(0);
        }
        return workingday;
    }
    
    /**
     * 
     * @param request
     * @return
     */
    protected Employeecontract getEmployeeContractFromRequest(HttpServletRequest request, EmployeecontractDAO employeecontractDAO) {
        
    	Employeecontract ec = null;
        ec = (Employeecontract)request.getSession().getAttribute("currentEmployeeContract");
        if (ec == null) {
            ec = (Employeecontract)request.getSession().getAttribute("loginEmployeeContract");
        }
        return ec;
    }
    
    /**
     * 
     * @param reportForm
     * @param ec
     * @return Returns the adequate {@link Workingday} for the selected date in the reportForm and the given
     * {@link Employeecontract}. If this workingday does not exist in the database so far, a new one is created.
     * @throws ParseException
     */
    // getWorkingdayForReportformAndEmployeeContract have a new parameter, boolean
    protected Workingday getWorkingdayForReportformAndEmployeeContract(ShowDailyReportForm reportForm, Employeecontract ec, WorkingdayDAO workingdayDAO, boolean nullPruefung) throws Exception {
        String dayString = reportForm.getDay();
        String monthString = reportForm.getMonth();
        String yearString = reportForm.getYear();
        
        TimereportHelper th = new TimereportHelper();
        Date tmp = th.getDateFormStrings(dayString, monthString, yearString, true);
        
        java.sql.Date refDate = new java.sql.Date(tmp.getTime());
        
        Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(refDate, ec.getId());
        if (workingday == null && nullPruefung) {
            workingday = new Workingday();
            workingday.setRefday(refDate);
            workingday.setEmployeecontract(ec);
        }
        return workingday;
    }
    
    protected List<OptionItem> getSerialDayList() {
        int maxDays = GlobalConstants.MAX_SERIAL_BOOKING_DAYS;
        List<OptionItem> days = new ArrayList<OptionItem>();
        days.add(new OptionItem("0", "--"));
        String dayValue = "";
        String dayLabel = "";
        for (int i = 1; i <= maxDays; i++) {
            if (i < 10) {
                dayLabel = "0" + i;
                //				dayValue = "0" + i;
            } else if (i >= 10) {
                dayLabel = "" + i;
                //				dayValue = "" + i;
            }
            dayValue = "" + i;
            days.add(new OptionItem(dayValue, dayLabel));
        }
        return days;
    }
}
