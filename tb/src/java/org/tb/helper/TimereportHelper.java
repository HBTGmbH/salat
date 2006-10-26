package org.tb.helper;

import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionMessages;
import org.tb.GlobalConstants;
import org.tb.bdom.Monthlyreport;
import org.tb.bdom.Timereport;
import org.tb.bdom.Vacation;
import org.tb.persistence.MonthlyreportDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.VacationDAO;
import org.tb.util.DateUtils;
import org.tb.web.form.AddDailyReportForm;
import org.tb.web.form.ShowDailyReportForm;
import org.tb.web.form.UpdateDailyReportForm;

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
	
	/**
	 * calculates worktime from begin/end times in form
	 * 
	 * @param UpdateDailyReportForm form
	 * 
	 * @return double - decimal hours
	 */
	public static double calculateTime(UpdateDailyReportForm form) {
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
	
	/**
	 * calculates daily sum of hours for 'W' timereports
	 * 
	 * @param List<Timereport> allReports
	 * 
	 * @return double - decimal hours
	 */
	public static double calculateDailyHourSum(List<Timereport> allReports) {
		double sum = 0.0;
				
		if ((allReports != null) && (allReports.size() > 0)) {
			for (Iterator iter = allReports.iterator(); iter.hasNext();) {
				Timereport tr = (Timereport) iter.next();
				// exclude all non-working timereports
				if (tr.getSortofreport().equals("W"))
					sum += tr.getHours();
			}
		}
		
		return sum;
	}
	
	/**
	 * calculates sum of hours for a list of timereports
	 * 
	 * @param List<Timereport> allReports
	 * 
	 * @return double - decimal hours
	 */
	public static double calculateTimereportWorkingHourSum(List<Timereport> allReports) {
		double sum = 0.0;
				
		if ((allReports != null) && (allReports.size() > 0)) {
			for (Iterator iter = allReports.iterator(); iter.hasNext();) {
				Timereport tr = (Timereport) iter.next();
				sum += tr.getHours();
			}
		}
		
		return sum;
	}
	
	/**
	 * also calculates sum of hours for a list of timereports,
	 * but excludes tr with excludeId in calculation
	 * 
	 * @param List<Timereport> allReports
	 * @param long excludeId
	 * 
	 * @return double - decimal hours
	 */
	public static double calculateTimereportWorkingHourSum(List<Timereport> allReports, long excludeId) {
		double sum = 0.0;
				
		if ((allReports != null) && (allReports.size() > 0)) {
			for (Iterator iter = allReports.iterator(); iter.hasNext();) {
				Timereport tr = (Timereport) iter.next();
				if (tr.getId() != excludeId) sum += tr.getHours();
			}
		}
		
		return sum;
	}
	
	/**
	 * updates hour balance: adds or subtracts one day used
	 * 
	 * @param Timereport tr
	 * @param action: 1 or -1 (add or subtract when deleting or inserting a report)
	 * @param td - TimereportDAO being used
	 * @param md - MonthlyreportDAO being used
	 * 
	 * @return void
	 */
	public void updateMonthlyHourBalance (Timereport tr, int action, TimereportDAO td, MonthlyreportDAO md) {
		String year = DateUtils.getYearString(tr.getReferenceday().getRefdate());	// yyyy
		String month = DateUtils.getMonthString(tr.getReferenceday().getRefdate()); // MM
		
		long ecId = tr.getEmployeecontract().getId();
		
		Monthlyreport mr = 
			md.getMonthlyreportByYearAndMonthAndEmployeecontract(ecId, Integer.parseInt(year),
																				Integer.parseInt(month));
		if (mr == null) {
			// add new monthly report
			mr = md.setNewReport(tr.getEmployeecontract(), 
							Integer.parseInt(year), Integer.parseInt(month));
		} 
		
		double balance = 0.0;
		if (tr.getReferenceday().getWorkingday()) {
			List<Timereport> monthlyTimereports = 
				td.getTimereportsByMonthAndYearAndEmployeeContractId(ecId, 
						TimereportHelper.getMonthStringFromTimereport(tr), year);
		
			int numberOfDaysWithReports = countWorkDaysInMonthWithTimereports(ecId, year, month, monthlyTimereports, td); 
		
			double monthlySum = calculateTimereportWorkingHourSum(monthlyTimereports);
			double pmnull = numberOfDaysWithReports * tr.getEmployeecontract().getDailyWorkingTime();
			balance = monthlySum - pmnull;
		} else {
			// if not a workingday, just add/remove the hours to/from actual balance...
			balance = mr.getHourbalance() + (action*tr.getHours());		
		}
		// update entry in monthly report...
		mr.setHourbalance(new Double(balance));
		md.save(mr); 
	}
	
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
	
	/**
	 * checks if form input report has time overlap with existing report
	 * 
	 * @param Timereport tr
	 * @param AddDailyReportForm reportForm
	 * 
	 * @return boolean
	 */
	public static boolean checkTimeOverlap(Timereport tr, AddDailyReportForm reportForm) {
		
		int formBegin = reportForm.getSelectedHourBegin()*100 + reportForm.getSelectedMinuteBegin();
		int formEnd = reportForm.getSelectedHourEnd()*100 + reportForm.getSelectedMinuteEnd();
		int trBegin = tr.getBeginhour().intValue()*100 + tr.getBeginminute().intValue();
		int trEnd = tr.getEndhour().intValue()*100 + tr.getEndminute().intValue();
		
		return (checkOverlap(formBegin, formEnd, trBegin, trEnd));
	}
	
	/**
	 * checks if form input report has time overlap with existing report
	 * 
	 * @param Timereport tr
	 * @param UpdateDailyReportForm reportForm
	 * 
	 * @return boolean
	 */
	public static boolean checkTimeOverlap(Timereport tr, UpdateDailyReportForm reportForm) {
		
		int formBegin = reportForm.getSelectedHourBegin()*100 + reportForm.getSelectedMinuteBegin();
		int formEnd = reportForm.getSelectedHourEnd()*100 + reportForm.getSelectedMinuteEnd();
		int trBegin = tr.getBeginhour().intValue()*100 + tr.getBeginminute().intValue();
		int trEnd = tr.getEndhour().intValue()*100 + tr.getEndminute().intValue();
	
		return (checkOverlap(formBegin, formEnd, trBegin, trEnd));
	}
	
	/**
	 * checks if form input report has time overlap with existing report
	 * 
	 * @param Timereport tr
	 * @param int hrbegin
	 * @param int minbegin
	 * @param int hrend
	 * @param int minend
	 * 
	 * @return boolean
	 */
	public static boolean checkTimeOverlap(Timereport tr, int hrbegin, int minbegin, int hrend, int minend) {
		
		int formBegin = hrbegin*100 + minbegin;
		int formEnd = hrend*100 + minend;
		int trBegin = tr.getBeginhour().intValue()*100 + tr.getBeginminute().intValue();
		int trEnd = tr.getEndhour().intValue()*100 + tr.getEndminute().intValue();
		
		return (checkOverlap(formBegin, formEnd, trBegin, trEnd));
	}
	
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
	
	/**
	 * determines begin hour to set in add report form dependent on reports already existing for this day
	 * 
	 * @param long ecId
	 * @param td - TimereportDAO being used
	 * 
	 * @return int[] - hour/minute
	 */
	public int[] determineBeginTimeToDisplay(long ecId, TimereportDAO td, Date date) {
		int[] beginTime = new int[2];
		int trLatest = -1;
		beginTime[0] = GlobalConstants.BEGINHOUR;
		beginTime[1] = GlobalConstants.BEGINMINUTE;
		java.sql.Date currentDate = DateUtils.getSqlDate(date);
		List<Timereport> dailyReports = td.getTimereportsByDateAndEmployeeContractId(ecId, currentDate);
		
		for (Iterator iter = dailyReports.iterator(); iter.hasNext();) {
			Timereport tr = (Timereport) iter.next();
			int trEnd = 100*tr.getEndhour().intValue() + tr.getEndminute().intValue();
			if (trEnd > trLatest) trLatest = trEnd;
		}
		if (trLatest > 0) {
			beginTime[0] = trLatest/100;
			beginTime[1] = trLatest % 100;
		}
		
		return beginTime;
	}
	
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
}
