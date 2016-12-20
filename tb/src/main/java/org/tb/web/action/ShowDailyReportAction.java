package org.tb.web.action;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.hibernate.HibernateException;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;
import org.tb.bdom.Workingday;
import org.tb.bdom.comparators.TimereportByEmployeeAscComparator;
import org.tb.bdom.comparators.TimereportByEmployeeDescComparator;
import org.tb.bdom.comparators.TimereportByOrderAscComparator;
import org.tb.bdom.comparators.TimereportByOrderDescComparator;
import org.tb.bdom.comparators.TimereportByRefdayAscComparator;
import org.tb.bdom.comparators.TimereportByRefdayDescComparator;
import org.tb.helper.CustomerorderHelper;
import org.tb.helper.EmployeeHelper;
import org.tb.helper.SuborderHelper;
import org.tb.helper.TimereportHelper;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.OvertimeDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.WorkingdayDAO;
import org.tb.util.DateUtils;
import org.tb.web.form.ShowDailyReportForm;
import org.tb.web.util.OvertimeString;

/**
 * Action class for a timereport to be shown in the daily display
 * 
 * @author oda, th
 *
 */
public class ShowDailyReportAction extends DailyReportAction {
    
    private OvertimeDAO overtimeDAO;
    private CustomerorderDAO customerorderDAO;
    private TimereportDAO timereportDAO;
    private EmployeecontractDAO employeecontractDAO;
    private SuborderDAO suborderDAO;
    private EmployeeorderDAO employeeorderDAO;
    private PublicholidayDAO publicholidayDAO;
    private WorkingdayDAO workingdayDAO;
    private EmployeeDAO employeeDAO;
    
    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }
    public void setWorkingdayDAO(WorkingdayDAO workingdayDAO) {
        this.workingdayDAO = workingdayDAO;
    }
    public void setPublicholidayDAO(PublicholidayDAO publicholidayDAO) {
        this.publicholidayDAO = publicholidayDAO;
    }
    public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
        this.employeeorderDAO = employeeorderDAO;
    }
    public void setSuborderDAO(SuborderDAO suborderDAO) {
        this.suborderDAO = suborderDAO;
    }
    public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
        this.employeecontractDAO = employeecontractDAO;
    }
    public void setTimereportDAO(TimereportDAO timereportDAO) {
        this.timereportDAO = timereportDAO;
    }
    public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
        this.customerorderDAO = customerorderDAO;
    }
    public void setOvertimeDAO(OvertimeDAO overtimeDAO) {
        this.overtimeDAO = overtimeDAO;
    }
    
    private Long safeParse(String sValue) {
    	try {
    		return Long.parseLong(sValue);
    	} catch(NumberFormatException e) {
    		return null;
    	}
    }
    
    /**
     * 
     * @param ids
     * @return true, if deleting was successful, false otherwise
     */
    private boolean massDelete(String[] ids) {
    	try {
    		Arrays.stream(ids)
	    		.map(this::safeParse)
	    		.filter(longOrNull -> longOrNull != null)
	    		.forEach(timereportDAO::deleteTimereportById);
    		return true;
    	} catch(HibernateException e) {
    		return false;
    	}
    }
    
    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
        String task = request.getParameter("task");
        
        // delete the selected ids from the database and the continue as if this was a refreshTimereports task
        if("massdelete".equalsIgnoreCase(task)) {
        	String sIds = request.getParameter("ids");

        	if(!massDelete(sIds.split(","))) {
	            return mapping.findForward("error");
        	}
        	task = "refreshTimereports";
        }
        TimereportHelper th = new TimereportHelper();
        ShowDailyReportForm reportForm = (ShowDailyReportForm)form;
        request.getSession().setAttribute("vacationBudgetOverrun", false);
        request.getSession().removeAttribute("createWorklogFailed");
        request.getSession().removeAttribute("updateWorklogFailed");
        request.getSession().removeAttribute("deleteWorklogFailed");
        request.getSession().setAttribute("createWorklogMemoryFailed", false);
        request.getSession().setAttribute("projectIDExists", false);
        Employeecontract ec = getEmployeeContractFromRequest(request, employeecontractDAO);
        
        // check if special tasks initiated from the daily display need to be carried out...
        String sortModus = (String)request.getSession().getAttribute("timereportSortModus");
        if (sortModus == null || !(sortModus.equals("+") || sortModus.equals("-"))) {
            sortModus = "+";
            request.getSession().setAttribute("timereportSortModus", sortModus);
        }
        String sortColumn = (String)request.getSession().getAttribute("timereportSortColumn");
        if (sortColumn == null || sortColumn.trim().equals("")) {
            sortColumn = "employee";
            request.getSession().setAttribute("timereportSortColumn", sortColumn);
        }
        if(task != null) {
	        if ("sort".equals(task)) {
	            return doSort(mapping, request, sortModus, sortColumn);
	        } else if ("saveBegin".equals(task) || "saveBreak".equals(task)) {
	            return doSaveBeginOrBreak(mapping, request, th, reportForm, ec, task);
	            
	        } else if ("refreshTimereports".equals(task)) {
	            return doRefreshTimereports(mapping, request, th, reportForm, ec);
	            
	        } else if ("refreshOrders".equals(task)) {
	            return doRefreshOrders(mapping, request, th, reportForm);
	        } else if ("refreshSuborders".equals(task)) {
	            return doRefreshSuborders(mapping, request, th, reportForm);
	        } else if ("print".equals(task)) {
	            return doPrint(mapping, request.getSession(), reportForm);
	        } else if ("back".equalsIgnoreCase(task))  {
	            // just go back to main menu
                return mapping.findForward("backtomenu");
            } else {
                return mapping.findForward("success");
            }
        } else {
        	//*** initialisation ***
        	init(mapping, request, th, reportForm);
        	//TODO: Hier bitte findForward zurückgeben.
        	if (request.getParameter("day") != null && request.getParameter("month") != null && request.getParameter("year") != null) {
        		// these parameters are only set when user clicked on day in matrix view -> redirected to showDailyReport with specific date
        		String date = request.getParameter("year") + "-" + DateUtils.getMonthMMStringFromShortstring(request.getParameter("month")) + "-" + request.getParameter("day");
        		reportForm.setStartdate(date);
        	}
        	//  make sure that overtimeCompensation is set in the session so that the duration-dropdown-menu will be disabled for timereports with suborder uesa00
        	if (request.getSession().getAttribute("overtimeCompensation") == null
        			|| request.getSession().getAttribute("overtimeCompensation") != GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION) {
        		request.getSession().setAttribute("overtimeCompensation", GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION);
        	}
        }
        request.getSession().setAttribute("reportForm", reportForm);
        request.getSession().setAttribute("currentSuborderId", reportForm.getSuborderId());
        return mapping.findForward("success");
    }
    
	private ActionForward doPrint(ActionMapping mapping, HttpSession session, ShowDailyReportForm reportForm) {
		//*** task for print view ***
		//conversion and localization of day values
		Map<String, String> monthMap = new HashMap<String, String>();
		monthMap.put("Jan", "main.timereport.select.month.jan.text");
		monthMap.put("Feb", "main.timereport.select.month.feb.text");
		monthMap.put("Mar", "main.timereport.select.month.mar.text");
		monthMap.put("Apr", "main.timereport.select.month.apr.text");
		monthMap.put("May", "main.timereport.select.month.may.text");
		monthMap.put("Jun", "main.timereport.select.month.jun.text");
		monthMap.put("Jul", "main.timereport.select.month.jul.text");
		monthMap.put("Aug", "main.timereport.select.month.aug.text");
		monthMap.put("Sep", "main.timereport.select.month.sep.text");
		monthMap.put("Oct", "main.timereport.select.month.oct.text");
		monthMap.put("Nov", "main.timereport.select.month.nov.text");
		monthMap.put("Dec", "main.timereport.select.month.dec.text");
		session.setAttribute("MonthKey", monthMap.get(reportForm.getMonth()));
		if (reportForm.getLastmonth() != null) {
		    session.setAttribute("LastMonthKey", monthMap.get(reportForm.getLastmonth()));
		}
		return mapping.findForward("print");
	}
    
	private ActionForward doRefreshSuborders(ActionMapping mapping, HttpServletRequest request, TimereportHelper th, ShowDailyReportForm reportForm) {
		//*** task for refreshing suborders ***
		// refresh suborders to be displayed in the select menu
		SuborderHelper sh = new SuborderHelper();
		if (sh.refreshDailyOverviewSuborders(mapping, request, reportForm, suborderDAO, employeecontractDAO) != true) {
		    return mapping.findForward("error");
		} else {
		    @SuppressWarnings("unchecked")
			List<Timereport> timereports = (List<Timereport>)request.getSession().getAttribute("timereports");
		    request.getSession().setAttribute("labortime", th.calculateLaborTime(timereports));
		    request.getSession().setAttribute("maxlabortime", th.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
		    request.getSession().setAttribute("dailycosts", th.calculateDailyCosts(timereports));
		    request.getSession().setAttribute("reportForm", reportForm);
		    return mapping.findForward("success");
		}
	}
    
	private ActionForward doRefreshOrders(ActionMapping mapping, HttpServletRequest request, TimereportHelper th, ShowDailyReportForm reportForm) {
		//*** task for refreshing orders ***
		// refresh orders to be displayed in the select menu
		CustomerorderHelper ch = new CustomerorderHelper();
		if (ch.refreshOrders(mapping, request, reportForm, customerorderDAO, employeecontractDAO, suborderDAO) != true) {
		    return mapping.findForward("error");
		} else {
		    @SuppressWarnings("unchecked")
			List<Timereport> timereports = (List<Timereport>)request.getSession().getAttribute("timereports");
		    request.getSession().setAttribute("labortime", th.calculateLaborTime(timereports));
		    request.getSession().setAttribute("maxlabortime", th.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
		    request.getSession().setAttribute("dailycosts", th.calculateDailyCosts(timereports));
		    request.getSession().setAttribute("reportForm", reportForm);
		    return mapping.findForward("success");
		}
	}
	private ActionForward doRefreshTimereports(ActionMapping mapping, HttpServletRequest request, TimereportHelper th, ShowDailyReportForm reportForm, Employeecontract ec) {
		//*** task for refreshing timereports table
		// set start and end dates 
		String view = reportForm.getView();
		if (GlobalConstants.VIEW_MONTHLY.equals(view)) {
		    // monthly view -> create date and synchronize with end-/lastdate-fields
		    reportForm.setStartdate(reportForm.getYear() + "-" + DateUtils.getMonthMMStringFromShortstring(reportForm.getMonth()) + "-" + reportForm.getDay());
		    reportForm.setLastday(reportForm.getDay());
		    reportForm.setLastmonth(reportForm.getMonth());
		    reportForm.setLastyear(reportForm.getYear());
		    reportForm.setEnddate(reportForm.getStartdate());
		} else {
			Date startdate;
		    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
		    if (reportForm.getStartdate() != null) {
		        try {
		            startdate = simpleDateFormat.parse(reportForm.getStartdate());
		        } catch (ParseException e) {
		            startdate = new java.util.Date();
		        }
		    } else {
		        startdate = new java.util.Date();
		    }

		    Date enddate;
		    if (reportForm.getEnddate() != null) {
		        try {
		            enddate = simpleDateFormat.parse(reportForm.getEnddate());
		        } catch (ParseException e) {
		            enddate = startdate;
		        }
		    } else {
		        enddate = startdate;
		    }
		    // change startdate or enddate by buttons
		    if (request.getParameter("change") != null) {
		        try {
		            int change = Integer.parseInt(request.getParameter("change"));
		            if ("start".equals(request.getParameter("date"))) {
		                startdate = changeDate(startdate, change);
		            } else if ("end".equals(request.getParameter("date"))) {
		                enddate = changeDate(enddate, change);
		            }
		        } catch (NumberFormatException e) {
		            return mapping.findForward("error");
		        }
		    }
		    // no monthly view -> parse startdate and set day/month/year-fields
		    reportForm.setDay(DateUtils.getDayString(startdate));
		    reportForm.setMonth(DateUtils.getMonthShortString(startdate));
		    reportForm.setYear(DateUtils.getYearString(startdate));
		    reportForm.setStartdate(simpleDateFormat.format(startdate));
		    if (GlobalConstants.VIEW_DAILY.equals(view)) {
		        // daily view -> synchronize enddate and fields with startdate
		        reportForm.setLastday(reportForm.getDay());
		        reportForm.setLastmonth(reportForm.getMonth());
		        reportForm.setLastyear(reportForm.getYear());
		        reportForm.setEnddate(reportForm.getStartdate());
		    } else if (GlobalConstants.VIEW_CUSTOM.equals(view)) {
		        if (!enddate.before(startdate)) {
		            // custom view -> parse enddate and set lastday/-month/-year-fields
		            reportForm.setLastday(DateUtils.getDayString(enddate));
		            reportForm.setLastmonth(DateUtils.getMonthShortString(enddate));
		            reportForm.setLastyear(DateUtils.getYearString(enddate));
		            reportForm.setEnddate(simpleDateFormat.format(enddate));
		        } else {
		            // custom view -> parse startdate and set lastday/-month/-year-fields
		            // failsafe if enddate is before startdate
		            reportForm.setLastday(DateUtils.getDayString(startdate));
		            reportForm.setLastmonth(DateUtils.getMonthShortString(startdate));
		            reportForm.setLastyear(DateUtils.getYearString(startdate));
		            reportForm.setEnddate(simpleDateFormat.format(startdate));
		        }
		    } else {
		        return mapping.findForward("error");
		    }
		}
		
		/* avoid refresh ? */
		if (reportForm.getAvoidRefresh()) {
		    // get all necessary info from form
		    long employeeContractId = reportForm.getEmployeeContractId();
		    String orderSign = reportForm.getOrder();
		    /* set session attributes */
		    List<Customerorder> orders;
		    if (employeeContractId == 0 || employeeContractId == -1) {
		        orders = customerorderDAO.getCustomerorders();
		        request.getSession().setAttribute("currentEmployeeContract", null);
		    } else {
		        orders = customerorderDAO.getCustomerordersByEmployeeContractId(employeeContractId);
		        request.getSession().setAttribute("currentEmployeeContract", employeecontractDAO.getEmployeeContractById(employeeContractId));
		    }
		    List<Suborder> suborders = new LinkedList<Suborder>();
		    Customerorder customerorder = customerorderDAO.getCustomerorderBySign(orderSign);
		    if (orders.contains(customerorder)) {
		        suborders = customerorder.getSuborders();
		    } else if (!orders.isEmpty()) {
		        suborders = orders.get(0).getSuborders();
		    }
		    
		    // if <code>reportForm.showOnlyValid == true</code>, remove all invalid suborders
		    if(reportForm.getShowOnlyValid()) {
		    	Iterator<Suborder> iter = suborders.iterator();
		    	while(iter.hasNext()) {
		    		if(!iter.next().getCurrentlyValid()) {
		    			iter.remove();
		    		}
		    	}
		    }
		    
		    request.getSession().setAttribute("orders", orders);
		    request.getSession().setAttribute("suborders", suborders);
		    request.getSession().setAttribute("currentOrder", orderSign);
		    request.getSession().setAttribute("suborderFilerId", reportForm.getSuborderId());
		    request.getSession().setAttribute("view", reportForm.getView());
		    request.getSession().setAttribute("currentDay", reportForm.getDay());
		    request.getSession().setAttribute("currentMonth", reportForm.getMonth());
		    request.getSession().setAttribute("currentYear", reportForm.getYear());
		    request.getSession().setAttribute("lastDay", reportForm.getLastday());
		    request.getSession().setAttribute("lastMonth", reportForm.getLastmonth());
		    request.getSession().setAttribute("lastYear", reportForm.getLastyear());
		    request.getSession().setAttribute("timereports", new LinkedList<Timereport>());
		    return mapping.findForward("success");
		} else {
		    // refresh list of timereports to be displayed
		    boolean refreshSuccessful = refreshTimereports(mapping,
		            request,
		            reportForm,
		            customerorderDAO,
		            timereportDAO,
		            employeecontractDAO,
		            suborderDAO,
		            employeeorderDAO,
		            publicholidayDAO,
		            overtimeDAO);
		    if (refreshSuccessful) {
		        @SuppressWarnings("unchecked")
				List<Timereport> timereports = (List<Timereport>)request.getSession().getAttribute("timereports");
		        
		        //check if only project based training should be shown
		        if (reportForm.getShowTraining()) {
		            for (Iterator<Timereport> iterator = timereports.iterator(); iterator.hasNext();) {
		                Timereport c = iterator.next();
		                if (!c.getTraining()) {
		                    iterator.remove();
		                }
		            }
		        }
		        //check if overtime should be computed until enddate (not today)
		        if (reportForm.getShowOvertimeUntil()) {
		            if (ec.getId() != reportForm.getEmployeeContractId()) {
		                ec = employeecontractDAO.getEmployeeContractById(reportForm.getEmployeeContractId());
		            }
		            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
		            Date date = new Date();
		            try {
		            	date = simpleDateFormat.parse(reportForm.getEnddate());
		            } catch (ParseException e) {
		                throw new RuntimeException("this should not happen!");
		            }
		            if (GlobalConstants.VIEW_MONTHLY.equals(reportForm.getView())) {
		                GregorianCalendar gc = new GregorianCalendar();
		                gc.setTime(date);
		                int maxday = gc.getActualMaximum(Calendar.DAY_OF_MONTH);
		                gc.set(Calendar.DATE, maxday);
		                date = gc.getTime();
		            }
		            request.setAttribute("showOvertimeUntil", reportForm.getShowOvertimeUntil());
		            int overtime;
		            if (ec.getReportAcceptanceDate().before(date) && ec.getUseOvertimeOld() == false) {
		                Double overtimeStatic = ec.getOvertimeStatic();
		                int otStaticMinutes = (int)(overtimeStatic * 60);
		                Date dynamicDate = DateUtils.addDays(ec.getReportAcceptanceDate(), 1);
		                int overtimeDynamic = th.calculateOvertime(dynamicDate, date, ec, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO, true);
		                overtime = otStaticMinutes + overtimeDynamic;
		            } else {
		                overtime = th.calculateOvertime(ec.getValidFrom(), date, ec, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO, true);
		            }
		            boolean overtimeUntilIsNeg = overtime < 0;
		            request.getSession().setAttribute("overtimeUntilIsNeg", overtimeUntilIsNeg);
		            request.getSession().setAttribute("enddate", simpleDateFormat.format(date));
		            String overtimeString = OvertimeString.overtimeToString(overtime);
		            request.getSession().setAttribute("overtimeUntil", overtimeString);
		        }
		        
		        request.getSession().setAttribute("labortime", th.calculateLaborTime(timereports));
		        request.getSession().setAttribute("maxlabortime", th.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
		        request.getSession().setAttribute("dailycosts", th.calculateDailyCosts(timereports));
		        
		        if (reportForm.getEmployeeContractId() == -1) {
		            request.getSession().setAttribute("currentEmployeeId", -1l);
		            request.getSession().setAttribute("currentEmployee", GlobalConstants.ALL_EMPLOYEES);
		            request.getSession().setAttribute("currentEmployeeContract", null);
		        } else {
		            Employeecontract employeecontract = employeecontractDAO.getEmployeeContractById(reportForm.getEmployeeContractId());
		            request.getSession().setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
		            request.getSession().setAttribute("currentEmployee", employeecontract.getEmployee().getName());
		            request.getSession().setAttribute("currentEmployeeContract", employeecontract);
		        }
		        request.getSession().setAttribute("suborderFilerId", reportForm.getSuborderId());
		        
		        // refresh workingday				
		        Workingday workingday;
		        try {
		            workingday = refreshWorkingday(mapping, reportForm, request, employeecontractDAO, workingdayDAO);
		        } catch (Exception e) {
		            return mapping.findForward("error");
		        }
		        request.getSession().setAttribute("quittingtime", th.calculateQuittingTime(workingday, request, "quittingtime"));
		        //calculate Working Day End
		        request.getSession().setAttribute("workingDayEnds", th.calculateQuittingTime(workingday, request, "workingDayEnds"));
		        // save current filter settings in session
		        request.getSession().setAttribute("currentOrder", reportForm.getOrder());
		        request.getSession().setAttribute("suborderFilerId", reportForm.getSuborderId());
		        request.getSession().setAttribute("view", reportForm.getView());
		        request.getSession().setAttribute("currentDay", reportForm.getDay());
		        request.getSession().setAttribute("currentMonth", reportForm.getMonth());
		        request.getSession().setAttribute("currentYear", reportForm.getYear());
		        request.getSession().setAttribute("lastDay", reportForm.getLastday());
		        request.getSession().setAttribute("lastMonth", reportForm.getLastmonth());
		        request.getSession().setAttribute("lastYear", reportForm.getLastyear());
		        return mapping.findForward("success");
		    } else {
		        return mapping.findForward("error");
		    }
		}
	}
    
	private ActionForward doSaveBeginOrBreak(ActionMapping mapping, HttpServletRequest request, TimereportHelper th, ShowDailyReportForm reportForm, Employeecontract ec, String task) {
		//*** task for saving work starting time and saving work pausing time ***
		if (ec == null) {
		    request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator.");
		    return mapping.findForward("error");
		}
		Workingday workingday;
		try {
		    workingday = getWorkingdayForReportformAndEmployeeContract(reportForm, ec, workingdayDAO, true);
		} catch (Exception e) {
		    request.setAttribute("errorMessage", "More than one working day found");
		    return mapping.findForward("error");
		}
		if (task.equals("saveBegin")) {
		    workingday.setStarttimehour(reportForm.getSelectedWorkHourBegin());
		    workingday.setStarttimeminute(reportForm.getSelectedWorkMinuteBegin());
		} else if (task.equals("saveBreak")) {
		    workingday.setBreakhours(reportForm.getSelectedBreakHour());
		    workingday.setBreakminutes(reportForm.getSelectedBreakMinute());
		} else {
		    // unreachable code
		    assert false;
		}
		Workingday duplicate = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(workingday.getRefday(), workingday.getEmployeecontract().getId());
		if (duplicate != null) {
		    workingday.setId(duplicate.getId());
		}
		workingdayDAO.save(workingday);
		//show break time, quitting time and working day ends on the showdailyreport.jsp
		request.getSession().setAttribute("visibleworkingday", true);
		request.getSession().setAttribute("quittingtime", th.calculateQuittingTime(workingday, request, "quittingtime"));
		//calculate Working Day End
		request.getSession().setAttribute("workingDayEnds", th.calculateQuittingTime(workingday, request, "workingDayEnds"));
		request.getSession().setAttribute("reportForm", reportForm);
		return mapping.findForward("success");
	}
    
	private ActionForward doSort(ActionMapping mapping, HttpServletRequest request, String sortModus, String sortColumn) {
		//*** task for sorting the timereports table ***
		
		HttpSession session = request.getSession();
		
		@SuppressWarnings("unchecked")
		List<Timereport> timereports = (List<Timereport>)request.getSession().getAttribute("timereports");
		String column = request.getParameter("column");
		Comparator<Timereport> comparator = new TimereportByEmployeeAscComparator();
		if ("employee".equals(column)) {
		    if (sortColumn.equalsIgnoreCase(column) && sortModus.equals("+")) {
		        comparator = new TimereportByEmployeeDescComparator();
		        session.setAttribute("timereportSortModus", "-");
		    } else {
		        comparator = new TimereportByEmployeeAscComparator();
		        session.setAttribute("timereportSortModus", "+");
		        session.setAttribute("timereportSortColumn", column);
		    }
		} else if ("refday".equals(column)) {
		    if (sortColumn.equalsIgnoreCase(column) && sortModus.equals("+")) {
		        comparator = new TimereportByRefdayDescComparator();
		        session.setAttribute("timereportSortModus", "-");
		    } else {
		        comparator = new TimereportByRefdayAscComparator();
		        session.setAttribute("timereportSortModus", "+");
		        session.setAttribute("timereportSortColumn", column);
		    }
		} else if ("order".equals(column)) {
		    if (sortColumn.equalsIgnoreCase(column) && sortModus.equals("+")) {
		        comparator = new TimereportByOrderDescComparator();
		        session.setAttribute("timereportSortModus", "-");
		    } else {
		        comparator = new TimereportByOrderAscComparator();
		        session.setAttribute("timereportSortModus", "+");
		        session.setAttribute("timereportSortColumn", column);
		    }
		}
		Collections.sort(timereports, comparator);
		session.setAttribute("timereports", timereports);
		session.setAttribute("timereportComparator", comparator);
		return mapping.findForward("success");
	}
    
    private Date changeDate(Date date, int change) {
        Calendar cal = Calendar.getInstance();
        if (change != 0) {
            cal.setTime(date);
            cal.add(Calendar.DATE, change);
        } else {
            cal.setTime(new Date());
        }
        return cal.getTime();
    }
    
    /**
     * Called if no special task is given, called from menu eg. Prepares everything to show timereports of
     * logged-in user.
     * 
     * @param mapping
     * @param request
     * @param th
     * @param reportForm
     */
    private String init(ActionMapping mapping, HttpServletRequest request, TimereportHelper th, ShowDailyReportForm reportForm) {
        String forward = "success";
        Employee loginEmployee = (Employee)request.getSession().getAttribute("loginEmployee");
        Employeecontract ec = new EmployeeHelper().setCurrentEmployee(loginEmployee, request, employeeDAO, employeecontractDAO);
        if (ec == null) {
            request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator.");
            forward = "error";
            return forward;
        }
        List<Employeecontract> employeecontracts = employeecontractDAO.getVisibleEmployeeContractsOrderedByEmployeeSign();
        if (employeecontracts == null || employeecontracts.isEmpty()) {
            request.setAttribute("errorMessage", "No employees with valid contracts found - please call system administrator.");
            forward = "error";
            return forward;
        }
        
       	reportForm.setView(GlobalConstants.VIEW_DAILY);
       	reportForm.setShowOnlyValid(true);
       	request.getSession().setAttribute("view", GlobalConstants.VIEW_DAILY);
        request.getSession().setAttribute("employeecontracts", employeecontracts);
        request.getSession().setAttribute("years", DateUtils.getYearsToDisplay());
        request.getSession().setAttribute("days", DateUtils.getDaysToDisplay());
        request.getSession().setAttribute("months", DateUtils.getMonthsToDisplay());
        request.getSession().setAttribute("hours", DateUtils.getHoursToDisplay());
        request.getSession().setAttribute("breakhours", DateUtils.getCompleteHoursToDisplay());
        request.getSession().setAttribute("breakminutes", DateUtils.getMinutesToDisplay());
        request.getSession().setAttribute("hoursDuration", DateUtils.getHoursDurationToDisplay());
        request.getSession().setAttribute("minutes", DateUtils.getMinutesToDisplay());
        if (reportForm.getMonth() != null) {
            // call from list select change
            request.getSession().setAttribute("currentDay", reportForm.getDay());
            request.getSession().setAttribute("currentMonth", reportForm.getMonth());
            request.getSession().setAttribute("currentYear", reportForm.getYear());
            String sqlDateString = reportForm.getYear() + "-" + DateUtils.getMonthMMStringFromShortstring(reportForm.getMonth()) + "-" + reportForm.getDay();
            java.sql.Date sqlDate = java.sql.Date.valueOf(sqlDateString);
            Long currentEmployeeId = (Long)request.getSession().getAttribute("currentEmployeeId");
            if (currentEmployeeId == null || currentEmployeeId == 0) {
                currentEmployeeId = loginEmployee.getId();
                request.getSession().setAttribute("currentEmployeeId", currentEmployeeId);
            }
            List<Timereport> timereports;
            if (currentEmployeeId == -1) {
                // all employees
                timereports = timereportDAO.getTimereportsByDate(sqlDate);
            } else {
                timereports = timereportDAO.getTimereportsByDateAndEmployeeContractId(ec.getId(), sqlDate);
            }
            String laborTimeString = th.calculateLaborTime(timereports);
            request.getSession().setAttribute("labortime", laborTimeString);
            request.getSession().setAttribute("maxlabortime", th.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
            request.getSession().setAttribute("dailycosts", th.calculateDailyCosts(timereports));
            // refresh workingday
            Workingday workingday;
            try {
                workingday = refreshWorkingday(mapping, reportForm, request, employeecontractDAO, workingdayDAO);
            } catch (Exception e) {
                forward = "error";
                return forward;
            }
            
            if (workingday != null) {
                request.getSession().setAttribute("quittingtime", th.calculateQuittingTime(workingday, request, "quittingtime"));
                // calculate Working Day End
                request.getSession().setAttribute("workingDayEnds", th.calculateQuittingTime(workingday, request, "workingDayEnds"));
            }
            if (request.getSession().getAttribute("timereportComparator") != null) {
                @SuppressWarnings("unchecked")
				Comparator<Timereport> comparator = (Comparator<Timereport>)request.getSession().getAttribute("timereportComparator");
                Collections.sort(timereports, comparator);
            }
            request.getSession().setAttribute("timereports", timereports);
        } else {
            Date today = new Date();
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
            String todayString = simpleDateFormat.format(today);
            try {
                today = simpleDateFormat.parse(todayString);
            } catch (ParseException e) {
                throw new RuntimeException("this should not happen!");
            }
            java.sql.Date refDate = new java.sql.Date(today.getTime());
            Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(refDate, ec.getId());
            if (workingday != null) {
                // show break time, quitting time and working day ends on
                // the showdailyreport.jsp
                request.getSession().setAttribute("visibleworkingday", true);
                reportForm.setSelectedWorkHourBegin(workingday.getStarttimehour());
                reportForm.setSelectedWorkMinuteBegin(workingday.getStarttimeminute());
                reportForm.setSelectedBreakHour(workingday.getBreakhours());
                reportForm.setSelectedBreakMinute(workingday.getBreakminutes());
            } else {
                // don´t show break time, quitting time and working day ends
                // on the showdailyreport.jsp
                request.getSession().setAttribute("visibleworkingday", false);
                reportForm.setSelectedWorkHourBegin(0);
                reportForm.setSelectedWorkMinuteBegin(0);
                reportForm.setSelectedBreakHour(0);
                reportForm.setSelectedBreakMinute(0);
            }
            
            // call from main menu: set current month, year, timereports,
            // orders, suborders...
            Date dt = new Date();
            // get day string (e.g., '31') from java.util.Date
            String dayString = dt.toString().substring(8, 10);
            // get month string (e.g., 'Jan') from java.util.Date
            String monthString = dt.toString().substring(4, 7);
            // get year string (e.g., '2006') from java.util.Date
            int length = dt.toString().length();
            String yearString = dt.toString().substring(length - 4, length);
            request.getSession().setAttribute("currentDay", dayString);
            request.getSession().setAttribute("currentMonth", monthString);
            request.getSession().setAttribute("currentYear", yearString);
            request.getSession().setAttribute("lastDay", dayString);
            request.getSession().setAttribute("lastMonth", monthString);
            request.getSession().setAttribute("lastYear", yearString);
            
            // set in form
            reportForm.setDay(dayString);
            reportForm.setMonth(monthString);
            reportForm.setYear(yearString);
            
            String dateString = yearString + "-" + DateUtils.getMonthMMStringFromShortstring(monthString) + "-" + dayString;
            reportForm.setStartdate(dateString);
            request.getSession().setAttribute("startdate", dateString);
            reportForm.setEnddate(dateString);
            request.getSession().setAttribute("enddate", dateString);
            java.sql.Date sqlDate = java.sql.Date.valueOf(dateString);
            
            Long employeeId = (Long)request.getSession().getAttribute("currentEmployeeId");
            List<Timereport> timereports;
            if (employeeId != null && employeeId == -1) {
                timereports = timereportDAO.getTimereportsByDate(sqlDate);
            } else {
                timereports = timereportDAO.getTimereportsByDateAndEmployeeContractId(ec.getId(), sqlDate);
            }
            String laborTimeString = th.calculateLaborTime(timereports);
            request.getSession().setAttribute("labortime", laborTimeString);
            request.getSession().setAttribute("maxlabortime", th.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
            request.getSession().setAttribute("dailycosts", th.calculateDailyCosts(timereports));
            if (request.getSession().getAttribute("timereportComparator") != null) {
                @SuppressWarnings("unchecked")
				Comparator<Timereport> comparator = (Comparator<Timereport>)request.getSession().getAttribute("timereportComparator");
                Collections.sort(timereports, comparator);
            }
            request.getSession().setAttribute("timereports", timereports);
            request.getSession().setAttribute("quittingtime", th.calculateQuittingTime(workingday, request, "quittingtime"));
            // calculate Working Day End
            request.getSession().setAttribute("workingDayEnds", th.calculateQuittingTime(workingday, request, "workingDayEnds"));
            // orders
            List<Customerorder> orders = null;
            if (employeeId != null && employeeId == -1) {
                orders = customerorderDAO.getCustomerorders();
            } else {
                orders = customerorderDAO.getCustomerordersByEmployeeContractId(ec.getId());
            }
            request.getSession().setAttribute("orders", orders);
            if (!orders.isEmpty()) {
                request.getSession().setAttribute("suborders", suborderDAO.getSubordersByEmployeeContractId(ec.getId()));
            }
        }
        // vacation and overtime balance
        refreshVacationAndOvertime(request, ec, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO);
        
        // set current order = all orders
        request.getSession().setAttribute("currentOrder", "ALL ORDERS");
        request.getSession().setAttribute("currentOrderId", -1l);
        return forward;
    }
    
}
