package org.tb.helper;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.Globals;
import org.apache.struts.util.MessageResources;
import org.apache.struts.util.RequestUtils;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Statusreport;
import org.tb.bdom.Warning;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.StatusReportDAO;

public class StatusReportWarningHelper {

    /**
     * <p>Return the default message resources for the current module.</p>
     *
     * @param request The servlet request we are processing
     * @since Struts 1.1
     */
    private static MessageResources getResources(HttpServletRequest request) {
        return ((MessageResources) request.getAttribute(Globals.MESSAGES_KEY));
    }
    
    /**
     * <p>Return the user's currently selected Locale.</p>
     *
     * @param request The request we are processing
     */
    private static Locale getLocale(HttpServletRequest request) {
        return RequestUtils.getUserLocale(request, null);
    }

    public static void addWarnings(Employeecontract employeecontract, HttpServletRequest request, List<Warning> warnings, StatusReportDAO statusReportDAO, CustomerorderDAO customerorderDAO) {
        // statusreport due warning
        List<Customerorder> customerOrders = customerorderDAO.getCustomerOrdersByResponsibleEmployeeIdWithStatusReports(employeecontract.getEmployee().getId());
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);

        if (customerOrders != null && !customerOrders.isEmpty()) {
            
            java.util.Date now = new java.util.Date();
            
            for (Customerorder customerorder : customerOrders) {
                Date maxUntilDate = statusReportDAO.getMaxUntilDateForCustomerOrderId(customerorder.getId());
                
                if (maxUntilDate == null) {
                    maxUntilDate = customerorder.getFromDate();
                }
                
                Date checkDate = new Date(maxUntilDate.getTime());
                
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
                	warning.setSort(getResources(request).getMessage(getLocale(request), "main.info.warning.statusreport.finalreport"));
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
                    warning.setSort(getResources(request).getMessage(getLocale(request), "main.info.warning.statusreport.due"));
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
                warning.setSort(getResources(request).getMessage(getLocale(request), "main.info.warning.statusreport.acceptance"));
                warning.setText(statusreport.getCustomerorder().getSign() + " "
                        + statusreport.getCustomerorder().getShortdescription()
                        + " (ID:" + statusreport.getId() + " "
                        + getResources(request).getMessage(getLocale(request), "statusreport.from.text")
                        + ":" + simpleDateFormat.format(statusreport.getFromdate()) + " "
                        + getResources(request).getMessage(getLocale(request), "statusreport.until.text")
                        + ":" + simpleDateFormat.format(statusreport.getUntildate()) + " "
                        + getResources(request).getMessage(getLocale(request), "statusreport.from.text")
                        + ":" + statusreport.getSender().getName() + " "
                        + getResources(request).getMessage(getLocale(request), "statusreport.to.text")
                        + ":" + statusreport.getRecipient().getName() + ")");
                warning.setLink("/tb/do/EditStatusReport?srId=" + statusreport.getId());
                warnings.add(warning);
            }
        }
        
        if (warnings != null && !warnings.isEmpty()) {
            request.getSession().setAttribute("warnings", warnings);
            request.getSession().setAttribute("warningsPresent", true);
        } else {
            request.getSession().setAttribute("warningsPresent", false);
        }
	}
}
