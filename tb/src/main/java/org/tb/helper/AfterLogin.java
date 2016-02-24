package org.tb.helper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

import javax.servlet.http.HttpSession;

import org.apache.struts.util.MessageResources;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Statusreport;
import org.tb.bdom.Timereport;
import org.tb.bdom.Warning;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.OvertimeDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.StatusReportDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.util.DateUtils;
import org.tb.web.util.OvertimeString;

public class AfterLogin {
	private static final Logger LOG = LoggerFactory.getLogger(AfterLogin.class);

	private static List<Warning> checkEmployeeorders(Employeecontract employeecontract, EmployeeorderDAO employeeorderDAO, MessageResources resources, Locale locale) {
		List<Warning> warnings = new ArrayList<Warning>();
		
        for (Employeeorder employeeorder : employeeorderDAO.getEmployeeordersForEmployeeordercontentWarning(employeecontract)) {
            if (!employeecontract.getFreelancer() && !employeeorder.getSuborder().getNoEmployeeOrderContent()) {
                try {
                    if (employeeorder.getEmployeeordercontent() == null) {
                        throw new RuntimeException("null content");
                    } else if (employeeorder.getEmployeeordercontent() != null && employeeorder.getEmployeeordercontent().getCommitted_emp() != true
                            && employeeorder.getEmployeecontract().getEmployee().equals(employeecontract.getEmployee())) {
                        Warning warning = new Warning();
                        warning.setSort(resources.getMessage(locale, "employeeordercontent.thumbdown.text"));
                        warning.setText(employeeorder.getEmployeeOrderAsString());
                        warning.setLink("/tb/do/ShowEmployeeorder?employeeContractId=" + employeeorder.getEmployeecontract().getId());
                        warnings.add(warning);
                    } else if (employeeorder.getEmployeeordercontent() != null && employeeorder.getEmployeeordercontent().getCommitted_mgmt() != true
                            && employeeorder.getEmployeeordercontent().getContactTechHbt().equals(employeecontract.getEmployee())) {
                        Warning warning = new Warning();
                        warning.setSort(resources.getMessage(locale, "employeeordercontent.thumbdown.text"));
                        warning.setText(employeeorder.getEmployeeOrderAsString());
                        warning.setLink("/tb/do/ShowEmployeeorder?employeeContractId=" + employeeorder.getEmployeecontract().getId());
                        warnings.add(warning);
                    } else {
                        throw new RuntimeException("query suboptimal");
                    }
                } catch (Exception e) {
                    LOG.error(e.getMessage(), e);
                }
            }
        }
        
        return warnings;
	}
	
	public static List<Warning> createWarnings(Employeecontract employeecontract, Employeecontract loginEmployeeContract, EmployeeorderDAO employeeorderDAO, TimereportDAO timereportDAO, StatusReportDAO statusReportDAO, CustomerorderDAO customerorderDAO, MessageResources resources, Locale locale) {
        // warnings
        List<Warning> warnings = AfterLogin.checkEmployeeorders(employeecontract, employeeorderDAO, resources, locale);
        
        // timereport warning
        List<Timereport> timereports = timereportDAO.getTimereportsOutOfRangeForEmployeeContract(employeecontract);
        for (Timereport timereport : timereports) {
            Warning warning = new Warning();
            warning.setSort(resources.getMessage(locale, "main.info.warning.timereportnotinrange"));
            warning.setText(timereport.getTimeReportAsString());
            warnings.add(warning);
        }
        
        // timereport warning 2
        timereports = timereportDAO.getTimereportsOutOfRangeForEmployeeOrder(employeecontract);
        for (Timereport timereport : timereports) {
            Warning warning = new Warning();
            warning.setSort(resources.getMessage(locale, "main.info.warning.timereportnotinrangeforeo"));
            warning.setText(timereport.getTimeReportAsString() + " " + timereport.getEmployeeorder().getEmployeeOrderAsString());
            warnings.add(warning);
        }
        
        // timereport warning 3: no duration
        timereports = timereportDAO.getTimereportsWithoutDurationForEmployeeContractId(employeecontract.getId(), employeecontract.getReportReleaseDate());
        for (Timereport timereport : timereports) {
            Warning warning = new Warning();
            warning.setSort(resources.getMessage(locale, "main.info.warning.timereport.noduration"));
            warning.setText(timereport.getTimeReportAsString());
            if (loginEmployeeContract.equals(employeecontract)
                    || loginEmployeeContract.getEmployee().getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_BL)
                    || loginEmployeeContract.getEmployee().getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_PV)
                    || loginEmployeeContract.getEmployee().getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
                warning.setLink("/tb/do/EditDailyReport?trId=" + timereport.getId());
            }
            warnings.add(warning);
        }

        // statusreport due warning
        addWarnings(loginEmployeeContract, resources, locale, warnings, statusReportDAO, customerorderDAO);
        
        return warnings;
	}

    private static void addWarnings(Employeecontract employeecontract, MessageResources resources, Locale locale, List<Warning> warnings, StatusReportDAO statusReportDAO, CustomerorderDAO customerorderDAO) {
        // statusreport due warning
        List<Customerorder> customerOrders = customerorderDAO.getCustomerOrdersByResponsibleEmployeeIdWithStatusReports(employeecontract.getEmployee().getId());
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);

        if (customerOrders != null && !customerOrders.isEmpty()) {
            
            java.util.Date now = new java.util.Date();
            
            for (Customerorder customerorder : customerOrders) {
            	java.sql.Date maxUntilDate = statusReportDAO.getMaxUntilDateForCustomerOrderId(customerorder.getId());
                
                if (maxUntilDate == null) {
                    maxUntilDate = customerorder.getFromDate();
                }
                
                java.sql.Date checkDate = new java.sql.Date(maxUntilDate.getTime());
                
                GregorianCalendar calendar = new GregorianCalendar();
                calendar.setTime(checkDate);
                calendar.add(Calendar.MONTH, 12 / customerorder.getStatusreport());
                checkDate.setTime(calendar.getTimeInMillis());
                
                // final report due warning
                List<Statusreport> finalReports = statusReportDAO.getReleasedFinalStatusReportsByCustomerOrderId(customerorder.getId());
                if (customerorder.getStatusreport() > 0
                		&& customerorder.getUntilDate() != null
                		&& !customerorder.getUntilDate().after(now)
                		&& (finalReports == null || finalReports.isEmpty())) {
                	Warning warning = new Warning();
                	warning.setSort(resources.getMessage(locale, "main.info.warning.statusreport.finalreport"));
                	warning.setText(customerorder.getSign() + " " + customerorder.getShortdescription());
                	List<Statusreport> unreleasedReports = statusReportDAO.getUnreleasedFinalStatusReports(customerorder.getId(), employeecontract.getEmployee().getId(), maxUntilDate);
                	if (unreleasedReports != null && !unreleasedReports.isEmpty()) {
                		if (unreleasedReports.size() == 1) {
                			warning.setLink("/tb/do/EditStatusReport?srId=" + unreleasedReports.get(0).getId());
                		} else {
                			warning.setLink("/tb/do/ShowStatusReport?coId=" + customerorder.getId());
                		}
                	} else {
                		warning.setLink("/tb/do/CreateStatusReport?coId=" + customerorder.getId() + "&final=true");
                	}
                	warnings.add(warning);
                }
                
                // periodical report due warning
                if (!checkDate.after(now) && (customerorder.getUntilDate() == null || customerorder.getUntilDate().after(checkDate))) {
                    // show warning
                    Warning warning = new Warning();
                    warning.setSort(resources.getMessage(locale, "main.info.warning.statusreport.due"));
                    warning.setText(customerorder.getSign() + " " + customerorder.getShortdescription() + " (" + simpleDateFormat.format(checkDate) + ")");
                    List<Statusreport> unreleasedReports = statusReportDAO.getUnreleasedPeriodicalStatusReports(customerorder.getId(), employeecontract.getEmployee().getId(), maxUntilDate);
                    if (unreleasedReports != null && !unreleasedReports.isEmpty()) {
                        if (unreleasedReports.size() == 1) {
                            warning.setLink("/tb/do/EditStatusReport?srId=" + unreleasedReports.get(0).getId());
                        } else {
                            warning.setLink("/tb/do/ShowStatusReport?coId=" + customerorder.getId());
                        }
                        warnings.add(warning);
                    } else {
                    	if(finalReports.isEmpty()) {
                    		warning.setLink("/tb/do/CreateStatusReport?coId=" + customerorder.getId() + "&final=false");
                    		warnings.add(warning);
                    	}
                    }
                }
            }
        }
        
        // statusreport acceptance warning
        List<Statusreport> reportsToBeAccepted = statusReportDAO.getReleasedStatusReportsByRecipientId(employeecontract.getEmployee().getId());
        if (reportsToBeAccepted != null && !reportsToBeAccepted.isEmpty()) {
            for (Statusreport statusreport : reportsToBeAccepted) {
                Warning warning = new Warning();
                warning.setSort(resources.getMessage(locale, "main.info.warning.statusreport.acceptance"));
                warning.setText(statusreport.getCustomerorder().getSign() + " "
                        + statusreport.getCustomerorder().getShortdescription()
                        + " (ID:" + statusreport.getId() + " "
                        + resources.getMessage(locale, "statusreport.from.text")
                        + ":" + simpleDateFormat.format(statusreport.getFromdate()) + " "
                        + resources.getMessage(locale, "statusreport.until.text")
                        + ":" + simpleDateFormat.format(statusreport.getUntildate()) + " "
                        + resources.getMessage(locale, "statusreport.from.text")
                        + ":" + statusreport.getSender().getName() + " "
                        + resources.getMessage(locale, "statusreport.to.text")
                        + ":" + statusreport.getRecipient().getName() + ")");
                warning.setLink("/tb/do/EditStatusReport?srId=" + statusreport.getId());
                warnings.add(warning);
            }
        }
	}
    
    public static void handleOvertime(Employeecontract employeecontract, EmployeeorderDAO employeeorderDAO, PublicholidayDAO publicholidayDAO, TimereportDAO timereportDAO, OvertimeDAO overtimeDAO, HttpSession session) {
        TimereportHelper th = new TimereportHelper();
        Double overtimeStatic = employeecontract.getOvertimeStatic();
        int otStaticMinutes = (int)(overtimeStatic * 60);
        
        int overtime;
        if (employeecontract.getUseOvertimeOld() != null && !employeecontract.getUseOvertimeOld()) {
            //use new overtime computation with static + dynamic overtime
            //need the Date from the day after reportAcceptanceDate, so the latter is not used twice in overtime computation:
        	Date dynamicDate;
        	if(employeecontract.getReportAcceptanceDate() == null || employeecontract.getReportAcceptanceDate().equals(employeecontract.getValidFrom())) {
        		dynamicDate = employeecontract.getValidFrom();
        	} else {
        		dynamicDate = DateUtils.addDays(employeecontract.getReportAcceptanceDate(), 1);
        	}
            int overtimeDynamic = th.calculateOvertime(dynamicDate, new Date(), employeecontract, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO, true);
            overtime = otStaticMinutes + overtimeDynamic;
            // if after SALAT-Release 1.83, no Release was accepted yet, use old overtime computation
        } else {
            overtime = th.calculateOvertimeTotal(employeecontract, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO);
        }
        
        boolean overtimeIsNegative = overtime < 0;
        
        session.setAttribute("overtimeIsNegative", overtimeIsNegative);
        
        String overtimeString = OvertimeString.overtimeToString(overtime);
        session.setAttribute("overtime", overtimeString);
        
        //overtime this month
		Date start = new LocalDate().withDayOfMonth(1).toDate();
		Date currentDate = new Date();
		
		if (employeecontract.getValidFrom().after(start) && !employeecontract.getValidFrom().after(currentDate)) {
		    start = employeecontract.getValidFrom();
		}
		if (employeecontract.getValidUntil() != null && employeecontract.getValidUntil().before(currentDate) && !employeecontract.getValidUntil().before(start)) {
		    currentDate = employeecontract.getValidUntil();
		}
		int monthlyOvertime = 0;
		if (!(employeecontract.getValidUntil() != null && employeecontract.getValidUntil().before(start) || employeecontract.getValidFrom().after(currentDate))) {
		    monthlyOvertime = th.calculateOvertime(start, currentDate, employeecontract, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO, false);
		}
		boolean monthlyOvertimeIsNegative = monthlyOvertime < 0;
		session.setAttribute("monthlyOvertimeIsNegative", monthlyOvertimeIsNegative);
		String monthlyOvertimeString = OvertimeString.overtimeToString(monthlyOvertime);
		session.setAttribute("monthlyOvertime", monthlyOvertimeString);
		
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM");
		session.setAttribute("overtimeMonth", format.format(start));
        
        //vacation v2 extracted to VacationViewer:
        VacationViewer vw = new VacationViewer(employeecontract);
        vw.computeVacations(session, employeecontract, employeeorderDAO, timereportDAO);
    }
}
