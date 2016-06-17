/*
 * File:          $RCSfile$
 * Version:       $Revision$
 * 
 * Created:       04.12.2006 by cb
 * Last changed:  $Date$ by $Author$
 * 
 * Copyright (C) 2006 by HBT GmbH, www.hbt.de
 *
 */
package org.tb.helper.matrix;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Publicholiday;
import org.tb.bdom.Timereport;
import org.tb.helper.TimereportHelper;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.util.DateUtils;
import org.tb.web.form.ShowMatrixForm;

/**
 * @author cb
 * @since 04.12.2006
 */
public class MatrixHelper {

	private static final String HANDLING_RESULTED_IN_ERROR_ERRORMESSAGE = "HANDLING_ERROR_MESSAGE";
	/** conversion and localization of day values */
	private static final Map<String, String> MONTH_MAP = new HashMap<String, String>();
	/**conversion and localization of weekday values */
	private static final Map<Integer, String> WEEK_DAYS_MAP = new HashMap<Integer, String>();
	
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

	
	static {
		for(String mon : new String[]{"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"}) {
			MONTH_MAP.put(mon, "main.timereport.select.month." + mon.toLowerCase() + ".text");
		}

		WEEK_DAYS_MAP.put(2, "main.matrixoverview.weekdays.monday.text");
		WEEK_DAYS_MAP.put(3, "main.matrixoverview.weekdays.tuesday.text");
		WEEK_DAYS_MAP.put(4, "main.matrixoverview.weekdays.wednesday.text");
		WEEK_DAYS_MAP.put(5, "main.matrixoverview.weekdays.thursday.text");
		WEEK_DAYS_MAP.put(6, "main.matrixoverview.weekdays.friday.text");
		WEEK_DAYS_MAP.put(7, "main.matrixoverview.weekdays.saturday.text");
		WEEK_DAYS_MAP.put(1, "main.matrixoverview.weekdays.sunday.text");
	}
	
	private final TimereportDAO trDAO;
	private final EmployeecontractDAO ecDAO;
	private final PublicholidayDAO phDAO;
	private final CustomerorderDAO coDAO;
	private final SuborderDAO soDAO;
	private final EmployeeDAO eDAO;
	private final TimereportHelper th;
	
	public MatrixHelper(TimereportDAO trDAO, EmployeecontractDAO ecDAO, PublicholidayDAO phDAO, CustomerorderDAO coDAO, SuborderDAO soDAO, EmployeeDAO eDAO, TimereportHelper th) {
		this.trDAO = trDAO;
		this.ecDAO = ecDAO;
		this.phDAO = phDAO;
		this.coDAO = coDAO;
		this.soDAO = soDAO;
		this.eDAO = eDAO;
		this.th = th;
	}
    
    /**
     * @param dateFirst
     * @param dateLast
     * @param employeeId
     * @param method
     * @param customerOrderId
     * @return
     * @author cb
     * @since 08.02.2007
     */
    public ReportWrapper getEmployeeMatrix(Date dateFirst, Date dateLast, long employeeContractId, int method, long customerOrderId, boolean invoiceable, boolean nonInvoiceable) {
        Employeecontract employeecontract = employeeContractId != -1 ? ecDAO.getEmployeeContractById(employeeContractId) : null;
        Date validFrom = dateFirst;
        Date validUntil = dateLast;
        if(employeecontract != null) {
        	if(employeecontract.getValidFrom() != null && dateFirst.before(employeecontract.getValidFrom())) validFrom = employeecontract.getValidFrom();
        	if(employeecontract.getValidUntil() != null && dateLast.after(employeecontract.getValidUntil())) validUntil = employeecontract.getValidUntil();
        }
        
        List<Timereport> timeReportList;
        if(invoiceable || nonInvoiceable) {
	        timeReportList = queryTimereports(dateFirst, dateLast, employeeContractId, method,	customerOrderId);
	        
	        //filter billable orders if necessary
	        filterInvoiceable(timeReportList, invoiceable, nonInvoiceable);
        } else {
        	timeReportList = new ArrayList<Timereport>();
        }
        
        List<MergedReport> mergedReportList = new ArrayList<MergedReport>();
		//filling a list with new or merged 'mergedreports'
        for (Timereport timeReport : timeReportList) {
            String taskdescription = extendedTaskDescription(timeReport, employeecontract == null);
            Date date = timeReport.getReferenceday().getRefdate();
            long durationHours = timeReport.getDurationhours();
            long durationMinutes = timeReport.getDurationminutes();
            
            // if timereport-suborder is overtime compensation, check if taskdescription is empty. If so, write "Überstundenausgleich" into it
            // -> needed because overtime compensation should be shown in matrix overview! (taskdescription as if-clause in jsp!)
            if (timeReport.getSuborder().getSign().equals(GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
                if (taskdescription == null || taskdescription.length() == 0) {
                    taskdescription = GlobalConstants.OVERTIME_COMPENSATION_TEXT;
                }
            }
            
            //insert into list if its not empty
            mergeTimereport(mergedReportList, timeReport, taskdescription, date, durationHours, durationMinutes);
        }
        
		//set all empty bookingdays to 0, calculate sum of the bookingdays for each MergedReport and sort them
        for (MergedReport mergedReport : mergedReportList) {
            mergedReport.fillBookingDaysWithNull(dateFirst, dateLast);
            mergedReport.setSum();
            Collections.sort(mergedReport.getBookingDay());
        }
        
        List<Publicholiday> publicHolidayList = phDAO.getPublicHolidaysBetween(dateFirst, dateLast);
        
        List<DayAndWorkingHourCount> dayHoursCount = new ArrayList<DayAndWorkingHourCount>();
        double dayHoursTarget = fillDayHoursCount(dateFirst, dateLast, validFrom, validUntil, dayHoursCount, publicHolidayList);
        
        //setting publicholidays(status and name) and weekend for dayandworkinghourcount and bookingday in mergedreportlist
        handlePublicHolidays(dateFirst, dateLast, mergedReportList, dayHoursCount, publicHolidayList);
        
        //sort mergedreportlist by custom- and subordersign
        Collections.sort(mergedReportList);
        
        //calculate dayhourssum
        double dayHoursSum = 0.0;
        for (DayAndWorkingHourCount dayAndWorkingHourCount : dayHoursCount) {
            dayHoursSum += dayAndWorkingHourCount.getWorkingHour();
        }
        dayHoursSum = (dayHoursSum + 0.05) * 10;
        dayHoursSum = (int)dayHoursSum / 10.0;
        
        //calculate dayhourstarget
        if (employeeContractId == -1) {
            List<Employeecontract> employeeContractList = ecDAO.getEmployeeContracts();
            double dailyWorkingTime = 0.0;
            for (Employeecontract employeeContract : employeeContractList) {
                dailyWorkingTime += employeeContract.getDailyWorkingTime();
            }
            dayHoursTarget = dayHoursTarget * dailyWorkingTime;
        } else {
            dayHoursTarget = dayHoursTarget * employeecontract.getDailyWorkingTime();
        }
        dayHoursTarget = (dayHoursTarget + 0.05) * 10;
        dayHoursTarget = (int)dayHoursTarget / 10.0;
        
        //calculate dayhoursdiff
        double dayHoursDiff = dayHoursSum - dayHoursTarget;
        if (dayHoursDiff < 0) {
            dayHoursDiff = (dayHoursDiff - 0.05) * 10;
        } else {
            dayHoursDiff = (dayHoursDiff + 0.05) * 10;
        }
        dayHoursDiff = (int)dayHoursDiff / 10.0;
        
        return new ReportWrapper(mergedReportList, dayHoursCount, dayHoursSum, dayHoursTarget, dayHoursDiff);
    }
    
    private String extendedTaskDescription(Timereport tr, boolean withSign) {
    	StringBuilder sb = new StringBuilder();
    	if(withSign) {
	    	sb.append(tr.getEmployeecontract().getEmployee().getSign());
	    	sb.append(": ");
    	}
    	sb.append(tr.getTaskdescription());
    	sb.append(" (");
    	sb.append(tr.getDurationhours());
    	sb.append(":");
    	if(tr.getDurationminutes() < 10) sb.append("0");
    	sb.append(tr.getDurationminutes());
    	sb.append(")");
    	sb.append(LINE_SEPARATOR);
    	return sb.toString();
    }

	private double fillDayHoursCount(Date dateFirst, Date dateLast, Date validFrom, Date validUntil, List<DayAndWorkingHourCount> dayHoursCount, List<Publicholiday> publicHolidayList) {
		//fill dayhourscount list with dayandworkinghourcounts for the time between dateFirst and dateLast

		Calendar cal = GregorianCalendar.getInstance();
        cal.setTime(dateFirst);
        int day = 0;
        while (cal.getTime().after(dateFirst) && cal.getTime().before(dateLast) || cal.getTime().equals(dateFirst)
                || cal.getTime().equals(dateLast)) {
            day++;
            dayHoursCount.add(new DayAndWorkingHourCount(day, 0, cal.getTime()));
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        
        day = 0;
        cal.setTime(dateFirst);
        double dayHoursTarget = 0.0;
        while (cal.getTime().after(dateFirst) && cal.getTime().before(dateLast) || cal.getTime().equals(dateFirst)
                || cal.getTime().equals(dateLast)) {
            day++;
            boolean dayIsPublicHoliday = false;
            //counting weekdays for dayhourstargettime
            if (cal.get(GregorianCalendar.DAY_OF_WEEK) != GregorianCalendar.SATURDAY && cal.get(GregorianCalendar.DAY_OF_WEEK) != GregorianCalendar.SUNDAY) {
                for (Publicholiday publicHoliday : publicHolidayList) {
                    if (publicHoliday.getRefdate().equals(cal.getTime())) {
                        dayIsPublicHoliday = true;
                        break;
                    }
                }
                if (!dayIsPublicHoliday && ( 
                		cal.getTime().after(validFrom) && 
                		cal.getTime().before(validUntil) || 
                		cal.getTime().equals(validFrom) || 
                		cal.getTime().equals(validUntil))) {
                    dayHoursTarget++;
                }
            }
            //setting publicholidays and weekend for dayhourscount(status and name)
            for (DayAndWorkingHourCount dayAndWorkingHourCount : dayHoursCount) {
                if (dayAndWorkingHourCount.getDay() == day) {
                    for (Publicholiday publicHoliday : publicHolidayList) {
                        if (publicHoliday.getRefdate().equals(cal.getTime())) {
                            dayHoursCount.get(dayHoursCount.indexOf(dayAndWorkingHourCount)).setPublicHoliday(true);
                            dayHoursCount.get(dayHoursCount.indexOf(dayAndWorkingHourCount)).setPublicHolidayName(publicHoliday.getName());
                        }
                    }
                    if (cal.get(GregorianCalendar.DAY_OF_WEEK) == GregorianCalendar.SATURDAY || cal.get(GregorianCalendar.DAY_OF_WEEK) == GregorianCalendar.SUNDAY) {
                        dayHoursCount.get(dayHoursCount.indexOf(dayAndWorkingHourCount)).setSatSun(true);
                    }
                    dayHoursCount.get(dayHoursCount.indexOf(dayAndWorkingHourCount)).setWeekDay(WEEK_DAYS_MAP.get(cal.get(Calendar.DAY_OF_WEEK)));
                }
            }
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
		return dayHoursTarget;
	}

	private void handlePublicHolidays(Date dateFirst, Date dateLast, List<MergedReport> mergedReportList, List<DayAndWorkingHourCount> dayHoursCount, List<Publicholiday> publicHolidayList) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(dateFirst);
        int day = 0;
        while (cal.getTime().after(dateFirst) && cal.getTime().before(dateLast) || cal.getTime().equals(dateFirst)
                || cal.getTime().equals(dateLast)) {
            day++;
            for (MergedReport mergedReport : mergedReportList) {
                for (BookingDay bookingDay : mergedReport.getBookingDay()) {
                    if (bookingDay.getDate().equals(cal.getTime())) {
                        //                        if (!dayHoursCount.isEmpty()) {
                        if (cal.get(GregorianCalendar.DAY_OF_WEEK) == GregorianCalendar.SATURDAY || cal.get(GregorianCalendar.DAY_OF_WEEK) == GregorianCalendar.SUNDAY) {
                            bookingDay.setSatSun(true);
                        }
                        for (int i = 0; i < dayHoursCount.size(); i++) {
                        	DayAndWorkingHourCount dayAndWorkingHourCount = dayHoursCount.get(i);
                            if (dayAndWorkingHourCount.getDay() == day) {
                                DayAndWorkingHourCount otherDayAndWorkingHourCount = new DayAndWorkingHourCount(day,
                                        (bookingDay.getDurationHours() * 60 + bookingDay.getDurationMinutes() + dayAndWorkingHourCount.getWorkingHour() * 60) / 60, bookingDay
                                                .getDate());
                                otherDayAndWorkingHourCount.setPublicHoliday(dayAndWorkingHourCount.getPublicHoliday());
                                otherDayAndWorkingHourCount.setPublicHolidayName(dayAndWorkingHourCount.getPublicHolidayName());
                                otherDayAndWorkingHourCount.setSatSun(dayAndWorkingHourCount.getSatSun());
                                otherDayAndWorkingHourCount.setWeekDay(dayAndWorkingHourCount.getWeekDay());
                                dayHoursCount.set(i, otherDayAndWorkingHourCount);
                                for (Publicholiday publicHoliday : publicHolidayList) {
                                    if (publicHoliday.getRefdate().equals(cal.getTime())) {
                                        bookingDay.setPublicHoliday(true);
                                    }
                                }
                            }
                        }
                    }
                }
                
            }
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
	}

	private void mergeTimereport(List<MergedReport> mergedReportList, Timereport timeReport, String taskdescription,
			Date date, long durationHours, long durationMinutes) {
		if (!mergedReportList.isEmpty()) {
		    //search until timereport matching mergedreport; merge bookingdays in case of match
			for(int mergedReportIndex = 0; mergedReportIndex < mergedReportList.size(); mergedReportIndex++) {
				MergedReport mergedReport = mergedReportList.get(mergedReportIndex);
		        if ((mergedReport.getCustomOrder().getSign() + mergedReport.getSubOrder().getSign()).equals(timeReport.getSuborder().getCustomerorder().getSign()
		                + timeReport.getSuborder().getSign())) {
		            for (BookingDay tempBookingDay : mergedReport.getBookingDay()) {
		                if (tempBookingDay.getDate().equals(date)) {
		                    mergedReport.mergeBookingDay(tempBookingDay, date, durationHours, durationMinutes, taskdescription);
		                    return;
		                }
		            }
		            //if bookingday is not available, add new or merge report by adding a new bookingday and substitute the mergedreportlist entrys 
	                mergedReport.addBookingDay(date, durationHours, durationMinutes, taskdescription);
	                mergedReportList.set(mergedReportIndex, mergedReport);
		            return;
		        }
		    }
		}
		//if bookingday is not available, add new or merge report by adding a new bookingday and substitute the mergedreportlist entrys 
		mergedReportList.add(new MergedReport(timeReport.getSuborder().getCustomerorder(), timeReport.getSuborder(), taskdescription, date, durationHours, durationMinutes));
	}

	private void filterInvoiceable(List<Timereport> timeReportList, boolean invoiceable, boolean nonInvoiceable) {
		if(invoiceable && nonInvoiceable) return;
		
		ArrayList<Timereport> tempTimeReportList = new ArrayList<Timereport>();
		for (Timereport timeReport : timeReportList) {
			boolean invoice = timeReport.getSuborder().getInvoice() == 'Y';
		    if ((invoiceable && invoice) || (nonInvoiceable && !invoice)) {
		        tempTimeReportList.add(timeReport);
		    }
		}
		timeReportList.clear();
		timeReportList.addAll(tempTimeReportList);
	}

	private List<Timereport> queryTimereports(Date dateFirst, Date dateLast, long employeeContractId, int method, long customerOrderId) {
		java.sql.Date beginSqlDate = new java.sql.Date(dateFirst.getTime());
        java.sql.Date endSqlDate = new java.sql.Date(dateLast.getTime());
		//choice of timereports by date, employeecontractid and/or customerorderid
        if (method == 1 || method == 3) {
            if (employeeContractId == -1) {
                return trDAO.getTimereportsByDates(beginSqlDate, endSqlDate);
            } else {
                return trDAO.getTimereportsByDatesAndEmployeeContractId(employeeContractId, beginSqlDate, endSqlDate);
            }
        } else if (method == 2 || method == 4) {
            if (employeeContractId == -1) {
                return trDAO.getTimereportsByDatesAndCustomerOrderId(beginSqlDate, endSqlDate, customerOrderId);
            } else {
                return trDAO.getTimereportsByDatesAndEmployeeContractIdAndCustomerOrderId(employeeContractId, beginSqlDate, endSqlDate, customerOrderId);
            }
        } else {
            throw new RuntimeException("this should not happen!");
        }
	}
	
	public Map<String, Object> refreshMergedReports(ShowMatrixForm reportForm) {
		// selected view and selected dates
		Map<String, Object> results = new HashMap<String, Object>();
		String selectedView = reportForm.getMatrixview();
		Date dateFirst;
		Date dateLast;
		try {
			if (selectedView.equals(GlobalConstants.VIEW_MONTHLY)) {
				dateFirst = th.getDateFormStrings("1", reportForm.getFromMonth(), reportForm.getFromYear(), false);
				int maxDays = getMaxDays(dateFirst);
				String maxDayString = getTwoDigitStr(maxDays);
				dateLast = th.getDateFormStrings(maxDayString, reportForm.getFromMonth(), reportForm.getFromYear(), false);
			} else if (selectedView.equals(GlobalConstants.VIEW_CUSTOM)) {
				dateFirst = th.getDateFormStrings(reportForm.getFromDay(), reportForm.getFromMonth(), reportForm.getFromYear(), false);
				if (reportForm.getUntilDay() == null || reportForm.getUntilMonth() == null || reportForm.getUntilYear() == null) {
					int maxDays = getMaxDays(dateFirst);
					String maxDayString = getTwoDigitStr(maxDays);
					reportForm.setUntilDay(maxDayString);
					reportForm.setUntilMonth(reportForm.getFromMonth());
					reportForm.setUntilYear(reportForm.getFromYear());
				}
				dateLast = th.getDateFormStrings(reportForm.getUntilDay(), reportForm.getUntilMonth(), reportForm.getUntilYear(), false);
			} else {
				throw new RuntimeException("no view type selected");
			}
		} catch (RuntimeException e) {
			throw e; // keep them going
		} catch (Exception e) {
			throw new RuntimeException("date cannot be parsed for form", e);
		}
		results.put("matrixview", selectedView);

		Customerorder order = coDAO.getCustomerorderBySign(reportForm.getOrder());
		ReportWrapper reportWrapper;
		Long ecId = reportForm.getEmployeeContractId();
		boolean isAcceptanceWarning = false;
		String acceptedBy = null;
		boolean isInvoiceable = reportForm.isInvoice();
		boolean isNonInvoiceable = reportForm.isNonInvoice();
		if (ecId == -1) {
			// consider timereports for all employees
			List<Customerorder> orders = coDAO.getCustomerorders();
			results.put("orders", orders);

			if (reportForm.getOrder() == null || reportForm.getOrder().equals("ALL ORDERS")) {
				// get the timereports for specific date, all employees, all
				// orders
				reportWrapper = getEmployeeMatrix(dateFirst, dateLast, ecId, GlobalConstants.MATRIX_SPECIFICDATE_ALLORDERS_ALLEMPLOYEES, -1, isInvoiceable, isNonInvoiceable);
			} else {
				// get the timereports for specific date, all employees,
				// specific order
				reportWrapper = getEmployeeMatrix(dateFirst, dateLast, ecId, GlobalConstants.MATRIX_SPECIFICDATE_SPECIFICORDERS_ALLEMPLOYEES, order.getId(), isInvoiceable, isNonInvoiceable);
			}

			results.put("currentEmployee", "ALL EMPLOYEES");
			results.put("currentEmployeeContract", null);
			results.put("currentEmployeeId", -1l);
			List<Employeecontract> ecList = ecDAO.getEmployeeContracts();
			for (Employeecontract employeeContract : ecList) {
				if (!employeeContract.getEmployee().getSign().equals("adm")) {
					isAcceptanceWarning = checkAcceptanceWarning(employeeContract, dateLast);
					if (!isAcceptanceWarning) {
						break;
					}
				}
			}
			if (isAcceptanceWarning) {
				acceptedBy = "";
			}
		} else {
			// consider timereports for specific employee
			Employeecontract employeeContract = ecDAO.getEmployeeContractById(ecId);
			if (employeeContract == null) {
				results.put(HANDLING_RESULTED_IN_ERROR_ERRORMESSAGE, "No employee contract found for employee - please call system administrator.");
				return results;
			}

			// also refresh orders/suborders to be displayed for specific
			// employee
			List<Customerorder> orders = coDAO.getCustomerordersByEmployeeContractId(ecId);
			results.put("orders", orders);
			if (orders.size() > 0) {
				results.put("suborders", soDAO.getSubordersByEmployeeContractId(employeeContract.getId()));
			}

			if (reportForm.getOrder() == null || reportForm.getOrder().equals("ALL ORDERS")) {
				// get the timereports for specific date, specific employee,
				// all orders
				reportWrapper = getEmployeeMatrix(dateFirst, dateLast, ecId, GlobalConstants.MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES, -1, isInvoiceable, isNonInvoiceable);
			} else {
				// get the timereports for specific date, specific employee,
				// specific order
				List<Customerorder> customerOrder = coDAO.getCustomerordersByEmployeeContractId(ecId);
				if (customerOrder.contains(order)) {
					reportWrapper = getEmployeeMatrix(dateFirst, dateLast, ecId, GlobalConstants.MATRIX_SPECIFICDATE_SPECIFICORDERS_SPECIFICEMPLOYEES, order.getId(), isInvoiceable, isNonInvoiceable);
				} else {
					reportWrapper = getEmployeeMatrix(dateFirst, dateLast, ecId, GlobalConstants.MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES, -1, isInvoiceable, isNonInvoiceable);
				}
			}

			results.put("currentEmployee", employeeContract.getEmployee().getName());
			results.put("currentEmployeeContract", employeeContract);
			results.put("currentEmployeeId", employeeContract.getEmployee().getId());

			// testing availability of the shown month
			boolean isInvalid = ((employeeContract.getValidUntil() != null && dateFirst.after(employeeContract.getValidUntil()))
					|| (employeeContract.getValidFrom() != null) && dateLast.before(employeeContract.getValidFrom()));
			results.put("invalid", isInvalid);

			isAcceptanceWarning = checkAcceptanceWarning(employeeContract, dateLast);
			if (isAcceptanceWarning) {
				Timereport timereport = trDAO.getLastAcceptedTimereportByDateAndEmployeeContractId(new java.sql.Date(dateLast.getTime()), employeeContract.getId());
				if (timereport != null) {
					Employee employee = eDAO.getEmployeeBySign(timereport.getAcceptedby());
					acceptedBy = employee.getFirstname() + " " + employee.getLastname() + " (" + employee.getStatus() + ")";
				}
			}
		}

		// refresh all relevant attributes
		String sOrder = reportForm.getOrder();
		if(acceptedBy != null) {
			results.put("acceptedby", acceptedBy);
		}
		results.put("acceptance", isAcceptanceWarning);
		results.put("mergedreports", reportWrapper.getMergedReportList());
		results.put("dayhourcounts", reportWrapper.getDayAndWorkingHourCountList());
		results.put("dayhourssum", reportWrapper.getDayHoursSum());
		results.put("dayhourstarget", reportWrapper.getDayHoursTarget());
		results.put("dayhoursdiff", reportWrapper.getDayHoursDiff());
		results.put("currentOrder", sOrder == null ? "ALL ORDERS" : sOrder);
		results.put("currentDay", reportForm.getFromDay());
		results.put("currentMonth", reportForm.getFromMonth());
		results.put("MonthKey", MONTH_MAP.get(reportForm.getFromMonth()));
		results.put("currentYear", reportForm.getFromYear());
		results.put("lastDay", reportForm.getUntilDay());
		results.put("lastMonth", reportForm.getUntilMonth());
		results.put("lastYear", reportForm.getUntilYear());
		GregorianCalendar gc = new GregorianCalendar();
		gc.setTime(dateFirst);
		results.put("daysofmonth", gc.getActualMaximum(GregorianCalendar.DAY_OF_MONTH));
		
		return results;
	}
	
	public Map<String, Object> handleNoArgs(ShowMatrixForm reportForm, Employeecontract ec, Employeecontract currentEc, Long currentEmployeeId, String currentMonth) {
		// selected view and selected dates
		Map<String, Object> results = new HashMap<String, Object>();
		// set daily view as standard
		reportForm.setMatrixview(GlobalConstants.VIEW_MONTHLY);
		results.put("matrixview", GlobalConstants.VIEW_MONTHLY);

		if (ec == null) {
			results.put(HANDLING_RESULTED_IN_ERROR_ERRORMESSAGE, "No employee contract found for employee - please call system administrator.");
			return results;
		}

		List<Employeecontract> employeeContracts = ecDAO.getVisibleEmployeeContractsOrderedByEmployeeSign();

		if (employeeContracts == null || employeeContracts.size() <= 0) {
			results.put(HANDLING_RESULTED_IN_ERROR_ERRORMESSAGE, "No employees with valid contracts found - please call system administrator.");
			return results;
		}
		results.put("employeecontracts", employeeContracts);
		results.put("days", DateUtils.getDaysToDisplay());
		results.put("years", DateUtils.getYearsToDisplay());

		boolean isInvoiceable = reportForm.isInvoice();
		boolean isNonInvoiceable = reportForm.isNonInvoice();
		
		ReportWrapper reportWrapper;
		int maxDays;
		if (reportForm.getFromMonth() != null) {
			// call from list select change
			results.put("currentDay", reportForm.getFromDay());
			results.put("currentMonth", reportForm.getFromMonth());
			results.put("MonthKey", MONTH_MAP.get(reportForm.getFromMonth()));
			results.put("currentYear", reportForm.getFromYear());

			Date dateFirst = initStartEndDate(th, "01", reportForm.getFromMonth(), reportForm.getFromYear(), reportForm.getFromMonth(), reportForm.getFromYear());
			
			maxDays = getMaxDays(dateFirst);
			String maxDayString = getTwoDigitStr(maxDays);
			Date dateLast = initStartEndDate(th, maxDayString, reportForm.getFromMonth(), reportForm.getFromYear(), reportForm.getFromMonth(), reportForm.getFromYear());

			Employeecontract employeecontract = currentEc;
			Long ecId = -1l;
			boolean isAcceptanceWarning = false;
			if (employeecontract != null) {
				ecId = employeecontract.getId();
				isAcceptanceWarning = checkAcceptanceWarning(employeecontract, dateLast);
			} else {
				List<Employeecontract> ecList = ecDAO.getEmployeeContracts();
				for (Employeecontract employeeContract : ecList) {
					if (!employeeContract.getEmployee().getSign().equals("adm")) {
						isAcceptanceWarning = checkAcceptanceWarning(employeeContract, dateLast);
						if (!isAcceptanceWarning) {
							break;
						}
					}
				}
			}
			results.put("acceptance", isAcceptanceWarning);
			if (isAcceptanceWarning) {
				Timereport tr = trDAO.getLastAcceptedTimereportByDateAndEmployeeContractId(new java.sql.Date(dateLast.getTime()), employeecontract.getId());
				Employee employee = eDAO.getEmployeeBySign(tr.getAcceptedby());
				results.put("acceptedby", employee.getFirstname() + " " + employee.getLastname() + " (" + employee.getStatus() + ")");
			}

			reportWrapper = getEmployeeMatrix(dateFirst, dateLast, ecId, GlobalConstants.MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES, reportForm.getOrderId(), isInvoiceable, isNonInvoiceable);
		} else {

			// call from main menu: set current month, year,
			// orders, suborders...
			Date dt = new Date();
			// get day string (e.g., '31') from java.util.Date
			String dayString = dt.toString().substring(8, 10);
			// get month string (e.g., 'Jan') from java.util.Date
			String monthString = dt.toString().substring(4, 7);
			// get year string (e.g., '2006') from java.util.Date
			int length = dt.toString().length();
			String yearString = dt.toString().substring(length - 4, length);

			// set Month for first call
			if (reportForm.getFromMonth() == null || reportForm.getFromMonth().trim().equalsIgnoreCase("")) {
				String month = currentMonth;
				if (month == null || month.trim().equals("")) {
					Date date = new Date();
					String[] dateArray = th.getDateAsStringArray(date);
					month = dateArray[1];
				}
				reportForm.setFromMonth(month);
			}

			String currMonth = reportForm.getFromMonth();
			String currYear = yearString;
			results.put("currentDay", dayString);
			results.put("currentMonth", reportForm.getFromMonth());
			results.put("MonthKey", MONTH_MAP.get(reportForm.getFromMonth()));
			results.put("currentYear", yearString);

			reportForm.setFromDay("01");
			reportForm.setFromMonth(monthString);
			reportForm.setFromYear(yearString);
			reportForm.setOrderId(-1);
			// reportForm.setInvoice(false);

			results.put("lastDay", dayString);
			results.put("lastMonth", monthString);
			results.put("lastYear", yearString);

			Date dateFirst = initStartEndDate(th, "01", currMonth, currYear, monthString, yearString);

			maxDays = getMaxDays(dateFirst);
			String maxDayString = getTwoDigitStr(maxDays);
			Date dateLast = initStartEndDate(th, maxDayString, currMonth, currYear, monthString, yearString);

			Long ecId = -1l;
			boolean newAcceptance = false;
			if (ec != null) {
				ecId = ec.getId();
				if (!ec.getAcceptanceWarningByDate(dateLast)) {
					if (ec.getReportAcceptanceDate() != null && !dateLast.after(ec.getReportAcceptanceDate())) {
						newAcceptance = true;
						Employee employee = eDAO.getEmployeeBySign(trDAO.getLastAcceptedTimereportByDateAndEmployeeContractId(new java.sql.Date(dateLast.getTime()), ec.getId()).getAcceptedby());
						results.put("acceptedby", employee.getFirstname() + " " + employee.getLastname() + " (" + employee.getStatus() + ")");
					}
				}
			}
			results.put("acceptance", newAcceptance);

			// orders
			List<Customerorder> orders;
			if (currentEmployeeId != null && currentEmployeeId == -1) {
				orders = coDAO.getCustomerorders();
				results.put("currentEmployee", "ALL EMPLOYEES");
			} else {
				orders = coDAO.getCustomerordersByEmployeeContractId(ec.getId());
				if (currentEmployeeId != null) {
					results.put("currentEmployee", eDAO.getEmployeeById(currentEmployeeId).getName());
				}
			}
			results.put("orders", orders);
			results.put("currentOrder", "ALL ORDERS");
			if (orders.size() > 0) {
				results.put("suborders", soDAO.getSubordersByEmployeeContractId(ec.getId()));
			}
			
			reportWrapper = getEmployeeMatrix(dateFirst, dateLast, ecId, GlobalConstants.MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES, -1, isInvoiceable, isNonInvoiceable);
		}
		results.put("mergedreports", reportWrapper.getMergedReportList());
		results.put("dayhourcounts", reportWrapper.getDayAndWorkingHourCountList());
		results.put("dayhourssum", reportWrapper.getDayHoursSum());
		results.put("dayhourstarget", reportWrapper.getDayHoursTarget());
		results.put("dayhoursdiff", reportWrapper.getDayHoursDiff());
		results.put("daysofmonth", maxDays);
		return results;
	}

	private Date initStartEndDate(TimereportHelper th, String startEndStr, String currMonth, String currYear, String monthString, String yearString) {
		try {
			if (currMonth != null) {
				return th.getDateFormStrings(startEndStr, currMonth, currYear, false);
			} else {
				return th.getDateFormStrings(startEndStr, monthString, yearString, false);
			}
		} catch (Exception e) {
			System.out.println("this should not happen");
		}
		return new Date();
	}

	public boolean isHandlingError(String key) {
		return HANDLING_RESULTED_IN_ERROR_ERRORMESSAGE.equals(key);
	}
	
	private boolean checkAcceptanceWarning(Employeecontract ec, Date dateLast) {
		if (!ec.getAcceptanceWarningByDate(dateLast)) {
			Date acceptanceDate = ec.getReportAcceptanceDate();
			return acceptanceDate != null && !dateLast.after(acceptanceDate);
		}
		return false;
	}

	private int getMaxDays(Date date) {
		GregorianCalendar gc = new GregorianCalendar();
		gc.setTime(date);
		return gc.getActualMaximum(Calendar.DAY_OF_MONTH);
	}

	private String getTwoDigitStr(int i) {
		if (i < 10) {
			return "0" + i;
		}
		return Integer.toString(i);
	}
}
