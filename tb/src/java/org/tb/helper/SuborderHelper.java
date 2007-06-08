package org.tb.helper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Suborder;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.SuborderDAO;
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
			SuborderDAO sd, EmployeecontractDAO ecd) {

//		EmployeeHelper eh = new EmployeeHelper();
//		String[] firstAndLast = eh.splitEmployeename(reportForm.getEmployeename());
//		Employeecontract ec = ecd.getEmployeeContractByEmployeeName(firstAndLast[0], firstAndLast[1]);
		
		Employeecontract ec = ecd.getEmployeeContractById(reportForm.getEmployeeContractId());
		
		if (ec == null) {
			request.setAttribute("errorMessage", 
					"No employee contract found for employee - please call system administrator.");
			return false;
		}
		
		String dateString = reportForm.getReferenceday();
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		Date date;
		try {
			date = simpleDateFormat.parse(dateString);
		} catch (Exception e) {
			throw new RuntimeException("error while parsing date");
		}
		
		// get suborders related to employee AND selected customer order...
		long customerorderId = reportForm.getOrderId();
		List<Suborder> theSuborders = sd.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(ec.getId(), customerorderId,date);
		
//		 prepare second collection of suborders sorted by description
//		List<Suborder> subordersByDescription = new ArrayList<Suborder>();
//		subordersByDescription.addAll(theSuborders);
//		Collections.sort(subordersByDescription, new SubOrderByDescriptionComparator());
		request.getSession().setAttribute("suborders", theSuborders);
//		request.getSession().setAttribute("subordersByDescription", subordersByDescription);
//		 get first Suborder to synchronize suborder lists
		Suborder so = theSuborders.get(0);
		request.getSession().setAttribute("currentSuborderId", so.getId());
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
			request.setAttribute("errorMessage", 
					"No employee contract found for employee - please call system administrator.");
			return false;
		}
		
		// get suborders related to employee AND selected customer order...
		long customerorderId = reportForm.getTrOrderId();
		request.getSession().setAttribute("suborders", 
					sd.getSubordersByEmployeeContractIdAndCustomerorderId(ec.getId(), customerorderId));
		
		return true;
	}
	
	public void adjustSuborderSignChanged(HttpServletRequest request, AddDailyReportForm reportForm,
			SuborderDAO sd) {

		Suborder so = sd.getSuborderById(reportForm.getSuborderSignId());
		request.getSession().setAttribute("currentSuborderId", so.getId()); 
	}
	
	public void adjustSuborderDescriptionChanged(HttpServletRequest request, AddDailyReportForm reportForm,
			SuborderDAO sd) {

		Suborder so = sd.getSuborderById(reportForm.getSuborderDescriptionId());
		request.getSession().setAttribute("currentSuborderId", so.getId()); 
	}
}
