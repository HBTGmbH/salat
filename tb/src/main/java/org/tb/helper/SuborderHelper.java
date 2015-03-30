package org.tb.helper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Suborder;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TicketDAO;
import org.tb.web.form.AddDailyReportForm;
import org.tb.web.form.ShowDailyReportForm;

/**
 * Helper class for suborder handling which does not directly deal with persistence
 * 
 * @author oda
 *
 */
public class SuborderHelper {

	/**
	 * refreshes suborder list after change of customer order in the 'add timereport' view 
	 * 
	 * @param mapping
	 * @param request
	 * @param reportForm - AddDailyReportForm
	 * @param sd - SuborderDAO being used
	 * @param ecd - EmployeecontractDAO being used
	 * 
	 * @return boolean
	 */
	public boolean refreshSuborders(ActionMapping mapping, HttpServletRequest request, AddDailyReportForm reportForm,
			SuborderDAO sd, TicketDAO td, EmployeecontractDAO ecd) {
		
		Employeecontract ec = ecd.getEmployeeContractById(reportForm.getEmployeeContractId());
		
		if (ec == null) {
			request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator.");
			return false;
		}
		
		String dateString = reportForm.getReferenceday();
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
		Date date;
		try {
			date = simpleDateFormat.parse(dateString);
		} catch (Exception e) {
			throw new RuntimeException("error while parsing date");
		}
		
		// get suborders related to employee AND selected customer order
		long customerorderId = reportForm.getOrderId();
		List<Suborder> theSuborders = sd.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(ec.getId(), customerorderId,date);		
		request.getSession().setAttribute("suborders", theSuborders);

		// set the first Suborder as current
		Suborder so = theSuborders.get(0);
		request.getSession().setAttribute("currentSuborderId", so.getId());
		
        JiraSalatHelper.setJiraTicketKeysForSuborder(request, td, so.getId());
        
        // if selected Suborder is Overtime Compensation, delete the previously automatically set daily working time
        // also make sure that overtimeCompensation is set in the session so that the duration-dropdown-menu will be disabled
        if (so != null && so.getSign().equalsIgnoreCase(GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
            reportForm.setSelectedHourDuration(0);
            reportForm.setSelectedMinuteDuration(0);
            if (	request.getSession().getAttribute("overtimeCompensation") == null || 
            		request.getSession().getAttribute("overtimeCompensation") 
                    != GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION) {
                request.getSession().setAttribute("overtimeCompensation", GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION);
            }
        }
        
        // if selected Suborder has a default-flag for projectbased training, set training in the form to true, so that the training-box in the jsp is checked
        if (so != null && so.getTrainingFlag()) {
            reportForm.setTraining(true);
        }
		
		return true;
	}
	
	/**
	 * refreshes suborder list after change of customer order in the 'show timereport' views
	 * 
	 * @param mapping
	 * @param request
	 * @param reportForm - ShowDailyReportForm
	 * @param sd - SuborderDAO being used
	 * @param ecd - EmployeecontractDAO being used
	 * 
	 * @return boolean
	 */
	public boolean refreshDailyOverviewSuborders(ActionMapping mapping, HttpServletRequest request, ShowDailyReportForm reportForm,
			SuborderDAO sd, EmployeecontractDAO ecd) {
		
		Employeecontract ec = ecd.getEmployeeContractById(reportForm.getEmployeeContractId());
		
		if (ec == null) {
			request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator.");
			return false;
		}
		
		// get suborders related to employee AND selected customer order...
		long customerorderId = reportForm.getTrOrderId();
		request.getSession().setAttribute("suborders", sd.getSubordersByEmployeeContractIdAndCustomerorderId(ec.getId(), customerorderId));
		
		return true;
	}
	
	public void adjustSuborderSignChanged(HttpServletRequest request, AddDailyReportForm reportForm, SuborderDAO sd) {

		Suborder so = sd.getSuborderById(reportForm.getSuborderSignId());
		request.getSession().setAttribute("currentSuborderId", so.getId()); 
	}
	
	public void adjustSuborderDescriptionChanged(HttpServletRequest request, AddDailyReportForm reportForm, SuborderDAO sd) {

		Suborder so = sd.getSuborderById(reportForm.getSuborderDescriptionId());
		request.getSession().setAttribute("currentSuborderId", so.getId()); 
	}
}
