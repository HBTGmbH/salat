package org.tb.helper;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionMessages;
import org.tb.GlobalConstants;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Overtime;
import org.tb.bdom.Publicholiday;
import org.tb.bdom.Timereport;
import org.tb.bdom.Vacation;
import org.tb.bdom.Workingday;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.OvertimeDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.VacationDAO;
import org.tb.util.DateUtils;
import org.tb.web.form.AddDailyReportForm;

/**
 * Helper class for timereport handling which does not directly deal with persistence
 * 
 * @author oda
 *
 */
public class TimereportHelper {

	public TimereportHelper() {
		// no actions
	}
	
	/**
	 * returns a string like 'Jan' from '2006-01-01'
	 * 
	 * @param Timereport tr
	 * 
	 * @return String
	 */
	public static String getMonthStringFromTimereport(Timereport tr) {
		Date dt = tr.getReferenceday().getRefdate();
		String trMonth = dt.toString().substring(5,7);
		int trMonthI = Integer.parseInt(trMonth);
		
		return DateUtils.monthShortStrings[trMonthI-1];
	}
	
	/**
	 * returns a string like '2006'
	 * 
	 * @param Timereport tr
	 * 
	 * @return String
	 */
	public static String getYearStringFromTimereport(Timereport tr) {		 
		Date dt = tr.getReferenceday().getRefdate();
		String yearString = dt.toString().substring(0,4);
		
		return(yearString);
	}
	
	/**
	 * returns a day string like '02'
	 * 
	 * @param Timereport tr
	 * 
	 * @return String
	 */
	public static String getDayStringFromTimereport(Timereport tr) {
		Date dt = tr.getReferenceday().getRefdate();
		String dayString = dt.toString().substring(8,10);
		
		return(dayString);
	}
	
	/**
	 * calculates worktime from begin/end times in form
	 * 
	 * @param AddDailyReportForm form
	 * 
	 * @return double - decimal hours
	 */
	public static double calculateTime(AddDailyReportForm form) {
		double worktime = 0.0;
		
		int hours = form.getSelectedHourEnd() - form.getSelectedHourBegin();
		int minutes = form.getSelectedMinuteEnd() - form.getSelectedMinuteBegin();
		
		if (minutes < 0) {
			hours -= 1;
			minutes += 60;
		}
		worktime = hours*1. + minutes/60.;
		
		return worktime;
	}
	
//	/**
//	 * calculates worktime from begin/end times in form
//	 * 
//	 * @param UpdateDailyReportForm form
//	 * 
//	 * @return double - decimal hours
//	 */
//	public static double calculateTime(UpdateDailyReportForm form) {
//		double worktime = 0.0;
//		
//		int hours = form.getSelectedHourEnd() - form.getSelectedHourBegin();
//		int minutes = form.getSelectedMinuteEnd() - form.getSelectedMinuteBegin();
//		
//		if (minutes < 0) {
//			hours -= 1;
//			minutes += 60;
//		}
//		worktime = hours*1. + minutes/60.;
//		
//		return worktime;
//	}
	
//	/**
//	 * calculates daily sum of hours for 'W' timereports
//	 * 
//	 * @param List<Timereport> allReports
//	 * 
//	 * @return double - decimal hours
//	 */
//	public static double calculateDailyHourSum(List<Timereport> allReports) {
//		double sum = 0.0;
//				
//		if ((allReports != null) && (allReports.size() > 0)) {
//			for (Iterator iter = allReports.iterator(); iter.hasNext();) {
//				Timereport tr = (Timereport) iter.next();
//				// exclude all non-working timereports
//				if (tr.getSortofreport().equals("W"))
//					sum += tr.getHours();
//			}
//		}
//		
//		return sum;
//	}
	
//	/**
//	 * calculates sum of hours for a list of timereports
//	 * 
//	 * @param List<Timereport> allReports
//	 * 
//	 * @return double - decimal hours
//	 */
//	public static double calculateTimereportWorkingHourSum(List<Timereport> allReports) {
//		double sum = 0.0;
//				
//		if ((allReports != null) && (allReports.size() > 0)) {
//			for (Iterator iter = allReports.iterator(); iter.hasNext();) {
//				Timereport tr = (Timereport) iter.next();
//				sum += tr.getHours();
//			}
//		}
//		
//		return sum;
//	}
	
//	/**
//	 * also calculates sum of hours for a list of timereports,
//	 * but excludes tr with excludeId in calculation
//	 * 
//	 * @param List<Timereport> allReports
//	 * @param long excludeId
//	 * 
//	 * @return double - decimal hours
//	 */
//	public static double calculateTimereportWorkingHourSum(List<Timereport> allReports, long excludeId) {
//		double sum = 0.0;
//				
//		if ((allReports != null) && (allReports.size() > 0)) {
//			for (Iterator iter = allReports.iterator(); iter.hasNext();) {
//				Timereport tr = (Timereport) iter.next();
//				if (tr.getId() != excludeId) sum += tr.getHours();
//			}
//		}
//		
//		return sum;
//	}
	
//	/**
//	 * updates hour balance: adds or subtracts one day used
//	 * 
//	 * @param Timereport tr
//	 * @param action: 1 or -1 (add or subtract when deleting or inserting a report)
//	 * @param td - TimereportDAO being used
//	 * @param md - MonthlyreportDAO being used
//	 * 
//	 * @return void
//	 */
//	public void updateMonthlyHourBalance (Timereport tr, int action, TimereportDAO td, MonthlyreportDAO md) {
//		String year = DateUtils.getYearString(tr.getReferenceday().getRefdate());	// yyyy
//		String month = DateUtils.getMonthString(tr.getReferenceday().getRefdate()); // MM
//		
//		long ecId = tr.getEmployeecontract().getId();
//		
//		Monthlyreport mr = 
//			md.getMonthlyreportByYearAndMonthAndEmployeecontract(ecId, Integer.parseInt(year),
//																				Integer.parseInt(month));
//		if (mr == null) {
//			// add new monthly report
//			mr = md.setNewReport(tr.getEmployeecontract(), 
//							Integer.parseInt(year), Integer.parseInt(month));
//		} 
//		
//		double balance = 0.0;
//		if (tr.getReferenceday().getWorkingday()) {
//			List<Timereport> monthlyTimereports = 
//				td.getTimereportsByMonthAndYearAndEmployeeContractId(ecId, 
//						TimereportHelper.getMonthStringFromTimereport(tr), year);
//		
//			int numberOfDaysWithReports = countWorkDaysInMonthWithTimereports(ecId, year, month, monthlyTimereports, td); 
//		
//			double monthlySum = calculateTimereportWorkingHourSum(monthlyTimereports);
//			double pmnull = numberOfDaysWithReports * tr.getEmployeecontract().getDailyWorkingTime();
//			balance = monthlySum - pmnull;
//		} else {
//			// if not a workingday, just add/remove the hours to/from actual balance...
//			balance = mr.getHourbalance() + (action*tr.getHours());		
//		}
//		// update entry in monthly report...
//		mr.setHourbalance(new Double(balance));
//		md.save(mr); 
//	}
	
	/**
	 * updates vacation: adds or subtracts one day used
	 * 
	 * @param tr
	 * @param action: 1 or -1 (add or subtract one day
	 * @param vd - VacationDAO being used
	 * 
	 * @return void
	 */
	public void updateVacation (Timereport tr, int action, VacationDAO vd) {
		String year = DateUtils.getYearString(tr.getReferenceday().getRefdate());
		long ecId = tr.getEmployeecontract().getId();
		
		// update entry in vacation...
		Vacation va = vd.getVacationByYearAndEmployeecontract(ecId, Integer.parseInt(year));
		
		if (va == null) {
			// should not be the case!
			vd.setNewVacation(tr.getEmployeecontract(), Integer.parseInt(year));
		} else {
			int used = va.getUsed().intValue() + action;
			va.setUsed(new Integer(used));
		}
		vd.save(va);
	}
	
	/**
	 * counts the days in month with timereports for a given employee 
	 * 
	 * @param long ecId
	 * @param String year
	 * @param String month
	 * @param List<Timereport> trList
	 * @param td - TimereportDAO being used
	 * 
	 * @return int
	 */
	public int countDaysInMonthWithTimereports(long ecId, String year, String month, 
			List<Timereport> trList, TimereportDAO td) {
		int numberOfDays = 0;
		String dateString = "";
		
		for (int i=1; i<=DateUtils.getLastDayOfMonth(year, month); i++) {
			if (i<10) {
				dateString = year + "-" + month + "-0" + i; 
			} else {
				dateString = year + "-" + month + "-" + i; 
			}
			java.sql.Date theDate = java.sql.Date.valueOf(dateString);
			List<Timereport> dailyTimereports = 
				td.getTimereportsByDateAndEmployeeContractId(ecId, theDate);
			if (dailyTimereports.size() > 0) numberOfDays++;
		}
		
		return numberOfDays;		
	}
	
	/**
	 * counts the working days in month with timereports for a given employee 
	 * 
	 * @param long ecId
	 * @param String year
	 * @param String month
	 * @param List<Timereport> trList
	 * @param td - TimereportDAO being used
	 * 
	 * @return int
	 */
	public int countWorkDaysInMonthWithTimereports(long ecId, String year, String month, 
			List<Timereport> trList, TimereportDAO td) {
		int numberOfDays = 0;
		String dateString = "";
		
		for (int i=1; i<=DateUtils.getLastDayOfMonth(year, month); i++) {
			if (i<10) {
				dateString = year + "-" + month + "-0" + i; 
			} else {
				dateString = year + "-" + month + "-" + i; 
			}
			java.sql.Date theDate = java.sql.Date.valueOf(dateString);
			List<Timereport> dailyTimereports = 
				td.getTimereportsByDateAndEmployeeContractId(ecId, theDate);
			if (dailyTimereports.size() > 0) {
				Timereport tr = (Timereport) dailyTimereports.get(0);
				if (tr.getReferenceday().getWorkingday()) {
					numberOfDays++;
				}
			}
		}
		
		return numberOfDays;		
	}
	
//	/**
//	 * checks if form input report has time overlap with existing report
//	 * 
//	 * @param Timereport tr
//	 * @param AddDailyReportForm reportForm
//	 * 
//	 * @return boolean
//	 */
//	public static boolean checkTimeOverlap(Timereport tr, AddDailyReportForm reportForm) {
//		
//		int formBegin = reportForm.getSelectedHourBegin()*100 + reportForm.getSelectedMinuteBegin();
//		int formEnd = reportForm.getSelectedHourEnd()*100 + reportForm.getSelectedMinuteEnd();
//		int trBegin = tr.getBeginhour().intValue()*100 + tr.getBeginminute().intValue();
//		int trEnd = tr.getEndhour().intValue()*100 + tr.getEndminute().intValue();
//		
//		return (checkOverlap(formBegin, formEnd, trBegin, trEnd));
//	}
	
//	/**
//	 * checks if form input report has time overlap with existing report
//	 * 
//	 * @param Timereport tr
//	 * @param UpdateDailyReportForm reportForm
//	 * 
//	 * @return boolean
//	 */
//	public static boolean checkTimeOverlap(Timereport tr, UpdateDailyReportForm reportForm) {
//		
//		int formBegin = reportForm.getSelectedHourBegin()*100 + reportForm.getSelectedMinuteBegin();
//		int formEnd = reportForm.getSelectedHourEnd()*100 + reportForm.getSelectedMinuteEnd();
//		int trBegin = tr.getBeginhour().intValue()*100 + tr.getBeginminute().intValue();
//		int trEnd = tr.getEndhour().intValue()*100 + tr.getEndminute().intValue();
//	
//		return (checkOverlap(formBegin, formEnd, trBegin, trEnd));
//	}
	
//	/**
//	 * checks if form input report has time overlap with existing report
//	 * 
//	 * @param Timereport tr
//	 * @param int hrbegin
//	 * @param int minbegin
//	 * @param int hrend
//	 * @param int minend
//	 * 
//	 * @return boolean
//	 */
//	public static boolean checkTimeOverlap(Timereport tr, int hrbegin, int minbegin, int hrend, int minend) {
//		
//		int formBegin = hrbegin*100 + minbegin;
//		int formEnd = hrend*100 + minend;
//		int trBegin = tr.getBeginhour().intValue()*100 + tr.getBeginminute().intValue();
//		int trEnd = tr.getEndhour().intValue()*100 + tr.getEndminute().intValue();
//		
//		return (checkOverlap(formBegin, formEnd, trBegin, trEnd));
//	}
	
	/**
	 * checks the overlap
	 * 
	 * @param int begin
	 * @param int end
	 * @param int refbegin
	 * @param int refend
	 * 
	 * @return boolean
	 */
	public static boolean checkOverlap(int begin, int end, int refbegin, int refend) {
		
		boolean overlap = false;
		
		//	total overlap
		if ((begin < refbegin) && (end > refend)) {
			overlap = true;
		}
		
		// partial overlap left
		if (!overlap) {
			if ((begin <= refbegin) && (end > refbegin)) {
				overlap = true;
			}
		}
		
		// partial overlap right
		if (!overlap) {
			if ((begin < refend) && (end >= refend)) {
				overlap = true;
			}
		}
		
		// "inside" overlap
		if (!overlap) {
			if ((begin >= refbegin) && (end <= refend)) {
				overlap = true;
			}
		}
		return overlap;
	}
	
//	/**
//	 * determines begin hour to set in add report form dependent on reports already existing for this day
//	 * 
//	 * @param long ecId
//	 * @param td - TimereportDAO being used
//	 * 
//	 * @return int[] - hour/minute
//	 */
//	public int[] determineBeginTimeToDisplay(long ecId, TimereportDAO td, Date date) {
//		int[] beginTime = new int[2];
//		int trLatest = -1;
//		beginTime[0] = GlobalConstants.BEGINHOUR;
//		beginTime[1] = GlobalConstants.BEGINMINUTE;
//		java.sql.Date currentDate = DateUtils.getSqlDate(date);
//		List<Timereport> dailyReports = td.getTimereportsByDateAndEmployeeContractId(ecId, currentDate);
//		
//		for (Iterator iter = dailyReports.iterator(); iter.hasNext();) {
//			Timereport tr = (Timereport) iter.next();
//			int trEnd = 100*tr.getEndhour().intValue() + tr.getEndminute().intValue();
//			if (trEnd > trLatest) trLatest = trEnd;
//		}
//		if (trLatest > 0) {
//			beginTime[0] = trLatest/100;
//			beginTime[1] = trLatest % 100;
//		}
//		
//		return beginTime;
//	}
	
	/**
	 * refreshes hours after change of begin/end times
	 * 
	 * @param AddDailyReportForm reportForm
	 * 
	 * @return void
	 */
	public static void refreshHours(AddDailyReportForm reportForm) {
		Double hours = reportForm.getHours();
		if (hours.doubleValue() < 0.0) {
			reportForm.setSelectedHourDuration(0);
			reportForm.setSelectedMinuteDuration(0);
			return;
		}
		
		reportForm.setHours(hours);
		int hourDuration = hours.intValue();
		int minuteDuration = (int) ((hours.doubleValue() - Math.floor(hours.doubleValue()))*60.);
		
		// clean possible truncation errors
		if (minuteDuration % GlobalConstants.MINUTE_INCREMENT == 1) minuteDuration--;
		if (minuteDuration % GlobalConstants.MINUTE_INCREMENT == GlobalConstants.MINUTE_INCREMENT-1) minuteDuration++;
		
		reportForm.setSelectedHourDuration(hourDuration);
		reportForm.setSelectedMinuteDuration(minuteDuration);
	}
		
	/**
	 * refreshes period after change of hours
	 * 
	 * @param HttpServletRequest request
	 * @param ActionMessages errors
	 * @param AddDailyReportForm reportForm
	 * 
	 * @return boolean
	 */
	public static boolean refreshPeriod(HttpServletRequest request, ActionMessages errors, AddDailyReportForm reportForm) {
		
		// calculate end hour/minute
		double hours = reportForm.getSelectedHourDuration() + reportForm.getSelectedMinuteDuration()/60.0;
		reportForm.setHoursDuration(new Double(hours));
		
		int hoursEnd = reportForm.getSelectedHourBegin() + reportForm.getHoursDuration().intValue();
		double dMinutes = (reportForm.getHoursDuration().doubleValue() - 
								Math.floor(reportForm.getHoursDuration().doubleValue()))*60.0;
		
		int minutesEnd = reportForm.getSelectedMinuteBegin() + new Double(dMinutes).intValue();
		
		// clean possible truncation errors
		if (minutesEnd % GlobalConstants.MINUTE_INCREMENT == 1) minutesEnd--;
		if (minutesEnd % GlobalConstants.MINUTE_INCREMENT == GlobalConstants.MINUTE_INCREMENT-1) minutesEnd++;
		
		if (minutesEnd >= 60) {
			minutesEnd -= 60;
			hoursEnd++;
		}
		
		reportForm.setSelectedHourEnd(hoursEnd);
		reportForm.setSelectedMinuteEnd(minutesEnd);
		
		return true;
	}
	
	
	/**
	 * 
	 * @param date (sql date)
	 * @param employeeContractId 
	 * @param timereportDAO
	 * @return Returns the working time for one day as an int array with length 2. The hours are at index[0], the minutes at index[1]. 
	 */
	public int[] getWorkingTimeForDateAndEmployeeContract(Date date, long employeeContractId, TimereportDAO timereportDAO) {
		int[] workingTime = new int[2];
		java.sql.Date currentDate = DateUtils.getSqlDate(date);
		List<Timereport> timereports = timereportDAO.getTimereportsByDateAndEmployeeContractId(employeeContractId, currentDate);
		int hours = 0;
		int minutes = 0;
		for(Timereport timereport : timereports) {
			hours += timereport.getDurationhours();
			minutes += timereport.getDurationminutes();
		}
		hours += minutes/60;
		minutes = minutes%60;
		workingTime[0] = hours;
		workingTime[1] = minutes;
		
		return workingTime;
	}
	
	
	/**
	 * 
	 * @param ecId
	 * @param td
	 * @param date
	 * @param workingday
	 * @return Returns int[]  0=hours 1=minutes
	 */
	public int[] determineBeginTimeToDisplay(long ecId, TimereportDAO td, Date date, Workingday workingday) {
		int[] beginTime = getWorkingTimeForDateAndEmployeeContract(date, ecId, td);
		if (workingday != null) {
			beginTime[0] += workingday.getStarttimehour();
			beginTime[1] += workingday.getStarttimeminute();
			beginTime[0] += workingday.getBreakhours();
			beginTime[1] += workingday.getBreakminutes();
			beginTime[0] += beginTime[1]/60;
			beginTime[1] = beginTime[1]%60;
		}
		return beginTime;
	}
	
	
	/**
	 * 
	 * @param ecId
	 * @param td
	 * @param date
	 * @param workingday
	 * @param tr
	 * @return
	 */
	public int[] determineTimesToDisplay(long ecId, TimereportDAO td, java.sql.Date date, Workingday workingday, Timereport tr) {
		List<Timereport> timereports = td.getTimereportsByDateAndEmployeeContractId(ecId, date);
		int[] displayTimes = new int[4];
		if (workingday != null) {
			displayTimes[0] = workingday.getStarttimehour();
			displayTimes[1] = workingday.getStarttimeminute();
			displayTimes[0] += workingday.getBreakhours();
			displayTimes[1] += workingday.getBreakminutes();
			Iterator<Timereport> it = timereports.iterator();
			Timereport timereport;
			while (it.hasNext()) {
				timereport = it.next();
				if (timereport.getId() == tr.getId()) {
					break;
				}
				displayTimes[0] += timereport.getDurationhours();
				displayTimes[1] += timereport.getDurationminutes();
			}
			displayTimes[2] = displayTimes[0] + tr.getDurationhours();
			displayTimes[3] = displayTimes[1] + tr.getDurationminutes();
			displayTimes[0] += displayTimes[1] / 60;
			displayTimes[1] = displayTimes[1] % 60;
			displayTimes[2] += displayTimes[3] / 60;
			displayTimes[3] = displayTimes[3] % 60;
		}		
		return displayTimes;
	}
	
	/**
	 * Calculates the overall labortime for a list of {@link Timereport}s.
	 * 
	 * @param timereports
	 * @return Returns the calculated time as String (hh:mm)
	 */
	public String calculateLaborTime(List<Timereport> timereports) {
		int [] labortime = calculateLaborTimeAsArray(timereports);
		int laborTimeHour = labortime[0];
		int laborTimeMinute = labortime[1];
				
		String laborTimeString;
		if (laborTimeHour < 10) {
			laborTimeString = "0"+laborTimeHour+":";
		} else {
			laborTimeString = laborTimeHour+":";
		}
		if (laborTimeMinute < 10) {
			return laborTimeString+"0"+laborTimeMinute;
		} else {
			return laborTimeString+laborTimeMinute;
		}
	}
	
	/**
	 * Calculates the overall labortime for a list of {@link Timereport}s.
	 * 
	 * @param timereports
	 * @return Returns the calculated time as int[] (index 0: hours, index 1: minutes)
	 */
	public int[] calculateLaborTimeAsArray(List<Timereport> timereports) {
		int[] labortime = new int[2];
		int laborTimeHour = 0;
		int laborTimeMinute = 0;
		
		for (Timereport timereport : timereports) {
			
			int hours = timereport.getDurationhours();
			int minutes = timereport.getDurationminutes();
			
			laborTimeHour += hours;
			laborTimeMinute += minutes;
		}
		laborTimeHour += (laborTimeMinute/60);
		laborTimeMinute = laborTimeMinute%60;
		labortime[0] = laborTimeHour;
		labortime[1] = laborTimeMinute;
		return labortime;
	}
	
	/**
	 * Checks, if the overall labortime for a list of {@link Timereport}s extends the maximal daily labor time.
	 * 
	 * @param timereports
	 * @param maximalDailyLaborTime
	 * @return Returns true, if the maximal labor time is extended, false otherwise
	 */
	public boolean checkLaborTimeMaximum(List<Timereport> timereports, double maximalDailyLaborTime) {
		int laborTimeHour = 0;
		int laborTimeMinute = 0;
		
		for (Timereport timereport : timereports) {
			
			int hours = timereport.getDurationhours();
			int minutes = timereport.getDurationminutes();
			
			laborTimeHour += hours;
			laborTimeMinute += minutes;
		}
		laborTimeHour += (laborTimeMinute/60);
		laborTimeMinute = laborTimeMinute%60;
		
		double laborTime = (double)laborTimeHour+((double)laborTimeMinute/60.0);
		return (laborTime > maximalDailyLaborTime);
	}
	
	/**
	 * 
	 * @param timereports
	 * @return Returns the sum of the costs of all given timereports.
	 */
	public double calculateDailyCosts(List<Timereport> timereports) {
		Double dailycosts = 0.0;
		for (Timereport timereport : timereports) {
			dailycosts += timereport.getCosts();
		}
		return dailycosts;
	}
	
	
	/** 
	 * 
	 * @param workingday
	 * @param request
	 * @return Returns a string with the calculated quitting time (hh:mm). If something fails (may happen for missing workingday, etc.), "n/a" will be returned.
	 */
	public String calculateQuittingTime(Workingday workingday, HttpServletRequest request) {
		String quittingTime;
		try {
			String labortimeString = (String) request.getSession().getAttribute("labortime");
			String[] laborTimeArray = labortimeString.split(":");
			String laborTimeHoursString = laborTimeArray[0];
			String laborTimeMinutesString = laborTimeArray[1];
			int laborTimeHoursInt = Integer.parseInt(laborTimeHoursString);
			int laborTimeMinutesInt = Integer.parseInt(laborTimeMinutesString);
			int quittingtimeHours = workingday.getStarttimehour() + workingday.getBreakhours() + laborTimeHoursInt;
			int quittingtimeMinutes = workingday.getStarttimeminute() + workingday.getBreakminutes() + laborTimeMinutesInt;
			quittingtimeHours += quittingtimeMinutes/60;
			quittingtimeMinutes = quittingtimeMinutes%60;
			// format return string
			quittingTime = "";
			if (quittingtimeHours<10) {
				quittingTime = quittingTime+"0";
			}
			quittingTime = quittingTime+quittingtimeHours+":";
			if (quittingtimeMinutes<10) {
				quittingTime = quittingTime+"0";
			}
			quittingTime = quittingTime + quittingtimeMinutes;
		} catch (Exception e) {
			quittingTime = "n/a";
		}
		return quittingTime;
	}
	
	/**
	 * 
	 * @param employeecontract
	 * @param employeeorderDAO
	 * @param publicholidayDAO
	 * @param timereportDAO
	 * @return Returns an int[] containing the hours at index 0 and the minutes at index 1.
	 */
	public int[] calculateOvertime(Employeecontract employeecontract, EmployeeorderDAO employeeorderDAO, PublicholidayDAO publicholidayDAO, TimereportDAO timereportDAO, OvertimeDAO overtimeDAO) {
//		int[] overtime = new int[2];
//		long overtimeHours;
//		long overtimeMinutes;
		
		Date today =  new Date();
//		SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");
//		String year = yearFormat.format(today);
	
		Date contractBegin = employeecontract.getValidFrom();
		
		return calculateOvertime(contractBegin, today, employeecontract, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO, true);
		
//		GregorianCalendar calendar = new GregorianCalendar();
//		
//		calendar.clear();
//		calendar.set(new Integer(year), Calendar.JANUARY, 1);
//		
//		// So = 1
//		// Mo = 2
//		// Di = 3
//		// Mi = 4
//		// Do = 5
//		// Fr = 6
//		// Sa = 7
//		int firstday = calendar.get(Calendar.DAY_OF_WEEK);
//				
//		int numberOfHolidays = 0;
//				
//		List<Publicholiday> holidays = publicholidayDAO.getPublicHolidaysBetween(contractBegin, today);
//		for (Publicholiday publicholiday : holidays) {
//			calendar.setTimeInMillis(publicholiday.getRefdate().getTime());
//			if ((calendar.get(Calendar.DAY_OF_WEEK) != 1) && (calendar.get(Calendar.DAY_OF_WEEK) != 8)) {
//				numberOfHolidays += 1;
//			}
//		}
//		
//		
//		long diffMillis;
//        long diffDays;
//        diffMillis = today.getTime() - contractBegin.getTime();
//        diffDays = (diffMillis+(60*60*1000))/(24*60*60*1000);
//        // 1 hour added because of possible differences caused by sommertime/wintertime
//        
//        // add 1 day (number of days are needed, not the difference)
//        diffDays += 1;
//        
//        if (diffDays < 0) {
//        	throw new RuntimeException("implementation error while calculating overtime");
//        }
//		long weeks = diffDays/7;			// how many complete weeks?
//		long days = diffDays%7;				// days of incomplete week
//		diffDays = diffDays - (weeks * 2); 	// subtract weekends of complete weeks
//		
//		// check weekdays of incomplete week
//		if (days > 0) {
//			if (firstday == 1) {
//				// firstday is a sunday			
//				diffDays -= 1;
//			} else {
//				if (firstday + days == 8) {
//					diffDays -= 1;
//				} else if (firstday + days > 8) {
//					diffDays -= 2;
//				}
//			}
//		}
//		
//		
//		// substract holidays
//		diffDays -= numberOfHolidays;
//		
//		// calculate working time
//		double dailyWorkingTime = employeecontract.getDailyWorkingTime() * 60;
//		if (dailyWorkingTime%1 != 0) {
//			throw new RuntimeException("daily working time must be mutiple of 0.05: "+employeecontract.getDailyWorkingTime());
//		}
//		long expectedWorkingTimeInMinutes = (long)dailyWorkingTime * diffDays;
//		long actualWorkingTimeInMinutes = 0;
//		List<Timereport> reports = timereportDAO.getTimereportsByEmployeeContractId(employeecontract.getId());
//		if (reports != null) {
//			for (Timereport timereport : reports) {
//				actualWorkingTimeInMinutes += (timereport.getDurationhours()*60) + timereport.getDurationminutes();
//			}
//		} 
//		long overtimeAdjustmentMinutes = 0;
//		List<Overtime> overtimes = overtimeDAO.getOvertimesByEmployeeContractId(employeecontract.getId());
//		for (Overtime ot : overtimes) {
//			overtimeAdjustmentMinutes += (ot.getTime()*60);
//		}
//		
//		overtimeMinutes = actualWorkingTimeInMinutes - expectedWorkingTimeInMinutes + overtimeAdjustmentMinutes;
//		overtimeHours = overtimeMinutes/60;
//		overtimeMinutes = overtimeMinutes%60;
//		
//		overtime[0] = (int)overtimeHours;
//		overtime[1] = (int)overtimeMinutes;
//		
//		return overtime;
	}
	
	public int[] calculateOvertime(Date start, Date end, Employeecontract employeecontract, EmployeeorderDAO employeeorderDAO, PublicholidayDAO publicholidayDAO, TimereportDAO timereportDAO, OvertimeDAO overtimeDAO, boolean useOverTimeAdjustment) {
		int[] overtime = new int[2];
		long overtimeHours;
		long overtimeMinutes;
		
		SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");
		String year = yearFormat.format(end);	
		
		GregorianCalendar calendar = new GregorianCalendar();
		
		calendar.clear();
		calendar.set(new Integer(year), Calendar.JANUARY, 1);
		
		// So = 1
		// Mo = 2
		// Di = 3
		// Mi = 4
		// Do = 5
		// Fr = 6
		// Sa = 7
		int firstday = calendar.get(Calendar.DAY_OF_WEEK);
				
		int numberOfHolidays = 0;
				
		List<Publicholiday> holidays = publicholidayDAO.getPublicHolidaysBetween(start, end);
		for (Publicholiday publicholiday : holidays) {
			calendar.setTimeInMillis(publicholiday.getRefdate().getTime());
			if ((calendar.get(Calendar.DAY_OF_WEEK) != 1) && (calendar.get(Calendar.DAY_OF_WEEK) != 8)) {
				numberOfHolidays += 1;
			}
		}
		
		
		long diffMillis;
        long diffDays;
        diffMillis = end.getTime() - start.getTime();
        diffDays = (diffMillis+(60*60*1000))/(24*60*60*1000);
        // 1 hour added because of possible differences caused by sommertime/wintertime
        
        // add 1 day (number of days are needed, not the difference)
        diffDays += 1;
        
        if (diffDays < 0) {
        	throw new RuntimeException("implementation error while calculating overtime");
        }
		long weeks = diffDays/7;			// how many complete weeks?
		long days = diffDays%7;				// days of incomplete week
		diffDays = diffDays - (weeks * 2); 	// subtract weekends of complete weeks
		
		// check weekdays of incomplete week
		if (days > 0) {
			if (firstday == 1) {
				// firstday is a sunday			
				diffDays -= 1;
			} else {
				if (firstday + days == 8) {
					diffDays -= 1;
				} else if (firstday + days > 8) {
					diffDays -= 2;
				}
			}
		}
		
		
		// substract holidays
		diffDays -= numberOfHolidays;
		
		// calculate working time
		double dailyWorkingTime = employeecontract.getDailyWorkingTime() * 60;
		if (dailyWorkingTime%1 != 0) {
			throw new RuntimeException("daily working time must be mutiple of 0.05: "+employeecontract.getDailyWorkingTime());
		}
		long expectedWorkingTimeInMinutes = (long)dailyWorkingTime * diffDays;
		long actualWorkingTimeInMinutes = 0;
		List<Timereport> reports = timereportDAO.getTimereportsByDatesAndEmployeeContractId(employeecontract.getId(), new java.sql.Date(start.getTime()), new java.sql.Date(end.getTime()));
		if (reports != null) {
			for (Timereport timereport : reports) {
				actualWorkingTimeInMinutes += (timereport.getDurationhours()*60) + timereport.getDurationminutes();
			}
		} 
		long overtimeAdjustmentMinutes = 0;
		
		if (useOverTimeAdjustment) {
			List<Overtime> overtimes = overtimeDAO
					.getOvertimesByEmployeeContractId(employeecontract.getId());
			for (Overtime ot : overtimes) {
				overtimeAdjustmentMinutes += (ot.getTime() * 60);
			}
		}		
		overtimeMinutes = actualWorkingTimeInMinutes - expectedWorkingTimeInMinutes + overtimeAdjustmentMinutes;
		overtimeHours = overtimeMinutes/60;
		overtimeMinutes = overtimeMinutes%60;
		
		overtime[0] = (int)overtimeHours;
		overtime[1] = (int)overtimeMinutes;
		
		return overtime;
	}
	
	/**
	 * Parses the Stings to create a {@link java.util.Date}. The day- and year-String are expected to represent integers. 
	 * The month-String must be of the sort 'Jan', 'Feb', 'Mar', ...
	 * 
	 *  
	 * 
	 * @param dayString
	 * @param monthString
	 * @param yearString
	 * @return Returns the date associated to the given Strings.
	 */
	public Date getDateFormStrings(String dayString, String monthString, String yearString, boolean useCurrentDateForFailure) throws Exception {
		int day = new Integer(dayString);
		int year = new Integer(yearString);
		int month = 0;
		
		if (GlobalConstants.MONTH_SHORTFORM_JANUARY.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_JANUARY;			
		} else if (GlobalConstants.MONTH_SHORTFORM_FEBRURAY.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_FEBRURAY;
		} else if (GlobalConstants.MONTH_SHORTFORM_MARCH.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_MARCH;
		} else if (GlobalConstants.MONTH_SHORTFORM_APRIL.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_APRIL;
		} else if (GlobalConstants.MONTH_SHORTFORM_MAY.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_MAY;
		} else if (GlobalConstants.MONTH_SHORTFORM_JUNE.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_JUNE;
		} else if (GlobalConstants.MONTH_SHORTFORM_JULY.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_JULY;
		} else if (GlobalConstants.MONTH_SHORTFORM_AUGUST.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_AUGUST;
		} else if (GlobalConstants.MONTH_SHORTFORM_SEPTEMBER.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_SEPTEMBER;
		} else if (GlobalConstants.MONTH_SHORTFORM_OCTOBER.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_OCTOBER;
		} else if (GlobalConstants.MONTH_SHORTFORM_NOVEMBER.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_NOVEMBER;
		} else if (GlobalConstants.MONTH_SHORTFORM_DECEMBER.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_DECEMBER;
		}
		
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		Date selectedDate;
		try {
			selectedDate = simpleDateFormat.parse(year+"-"+month+"-"+day);
		} catch (ParseException e) {
			//no date could be constructed - use current date instead
			if (useCurrentDateForFailure) {
				selectedDate = new Date();
			} else {
				throw new IllegalArgumentException("construction of the date failed");
			}
		}
		return selectedDate;
	}
	
	
	/**
	 * Transforms a {@link Date} into 3 {@link String}s, e.g. "09", "Feb", "2011".
	 * @param date
	 * @return Returns an array of strings with the
	 * day at index 0,
	 * month at index 1 and
	 * year at index 2. 
	 */
	public String[] getDateAsStringArray(java.util.Date date) {
		SimpleDateFormat dayFormat = new SimpleDateFormat("dd");
		SimpleDateFormat monthFormat = new SimpleDateFormat("MM");
		SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");
		String day = dayFormat.format(date);
		String year = yearFormat.format(date);
		String month = monthFormat.format(date);
		Integer monthValue = Integer.valueOf(month);
		if (monthValue == GlobalConstants.MONTH_INTVALUE_JANUARY){
			month = GlobalConstants.MONTH_SHORTFORM_JANUARY;
		} else if (monthValue == GlobalConstants.MONTH_INTVALUE_FEBRURAY) {
			month = GlobalConstants.MONTH_SHORTFORM_FEBRURAY;
		} else if (monthValue == GlobalConstants.MONTH_INTVALUE_MARCH) {
			month = GlobalConstants.MONTH_SHORTFORM_MARCH;
		} else if (monthValue == GlobalConstants.MONTH_INTVALUE_APRIL) {
			month = GlobalConstants.MONTH_SHORTFORM_APRIL;
		} else if (monthValue == GlobalConstants.MONTH_INTVALUE_MAY) {
			month = GlobalConstants.MONTH_SHORTFORM_MAY;
		} else if (monthValue == GlobalConstants.MONTH_INTVALUE_JUNE) {
			month = GlobalConstants.MONTH_SHORTFORM_JUNE;
		} else if (monthValue == GlobalConstants.MONTH_INTVALUE_JULY) {
			month = GlobalConstants.MONTH_SHORTFORM_JULY;
		} else if (monthValue == GlobalConstants.MONTH_INTVALUE_AUGUST) {
			month = GlobalConstants.MONTH_SHORTFORM_AUGUST;
		} else if (monthValue == GlobalConstants.MONTH_INTVALUE_SEPTEMBER) {
			month = GlobalConstants.MONTH_SHORTFORM_SEPTEMBER;
		} else if (monthValue == GlobalConstants.MONTH_INTVALUE_OCTOBER) {
			month = GlobalConstants.MONTH_SHORTFORM_OCTOBER;
		} else if (monthValue == GlobalConstants.MONTH_INTVALUE_NOVEMBER) {
			month = GlobalConstants.MONTH_SHORTFORM_NOVEMBER;
		} else if (monthValue == GlobalConstants.MONTH_INTVALUE_DECEMBER) {
			month = GlobalConstants.MONTH_SHORTFORM_DECEMBER;
		} 
		
		String[] dateArray = new String[3];
		dateArray[0] = day;
		dateArray[1] = month;
		dateArray[2] = year;
		
		return dateArray;
	}
	
	/**
	 * 
	 * @param request
	 * @return Returns the date associated the request. If parsing fails, the current date is returned.
	 */
	public Date getSelectedDateFromRequest(HttpServletRequest request) {
		String dayString = (String) request.getSession().getAttribute("currentDay");
		String monthString = (String) request.getSession().getAttribute("currentMonth");
		String yearString = (String) request.getSession().getAttribute("currentYear");
		
		Date date;
		try {
			date = getDateFormStrings(dayString, monthString, yearString, true);
		} catch (Exception e) {
			// if parsing fails, return current date
			date = new Date();
		}
		
		return date;
	}
	
	public List<Date> getDatesForTimePeriod(Date startDate, int numberOfLaborDays, PublicholidayDAO publicholidayDAO) {
		List<Date> dates = new ArrayList<Date>(numberOfLaborDays);
		GregorianCalendar calendar = new GregorianCalendar();
		int loopcounter = 0;
		for (int i = 0; i < numberOfLaborDays; i++) {			
			calendar.clear();
			calendar.setTime(startDate);
			int dayOfYear = calendar.get(Calendar.DAY_OF_YEAR);
			calendar.set(Calendar.DAY_OF_YEAR, dayOfYear+loopcounter);
			loopcounter++;
			int weekday = calendar.get(Calendar.DAY_OF_WEEK);
			if (weekday != 1 && weekday != 7) {
				// weekday is no sa, su
				Date laborDay = calendar.getTime();
				String holidayName = publicholidayDAO.getPublicHoliday(new java.sql.Date(laborDay.getTime()));
				if (holidayName == null || holidayName.equals("")) {
					// labor day is not a holiday
					dates.add(laborDay);
				} else {
					i--;
				}
			} else {
				i--;
			}
		}
		return dates;
	}
		
}
