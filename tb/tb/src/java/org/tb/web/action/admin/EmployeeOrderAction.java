package org.tb.web.action.admin;

import java.text.SimpleDateFormat;

import javax.servlet.http.HttpServletRequest;

import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddEmployeeOrderForm;

public abstract class EmployeeOrderAction extends LoginRequiredAction {
	
		
	
	/**
	 * Checks, if the employeeorder exists in the database. If it exists, the form is filled with the data and the session attribute "employeeorderalreadyexists" is set to true.
	 * @param request
	 * @param eoForm
	 */
	protected void checkDatabaseForEmployeeOrder(HttpServletRequest request, AddEmployeeOrderForm eoForm, EmployeecontractDAO employeecontractDAO, EmployeeorderDAO employeeorderDAO) {
		Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeId(eoForm.getEmployeeId());
		long employeecontractId = employeecontract.getId();
		long suborderId = eoForm.getSuborderId();
		
		Employeeorder employeeorder = employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderId(employeecontractId, suborderId);
		if (employeeorder != null) {
			request.getSession().setAttribute("employeeorderalreadyexists", true);
			//fill form with data from existing employeeorder
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
			eoForm.setValidFrom(simpleDateFormat.format(employeeorder.getFromDate()));
			eoForm.setValidUntil(simpleDateFormat.format(employeeorder.getUntilDate()));
			eoForm.setStandingorder(employeeorder.getStandingorder());
			eoForm.setDebithours(employeeorder.getDebithours());
			eoForm.setStatus(employeeorder.getStatus());
			eoForm.setStatusreport(employeeorder.getStatusreport());
		} else {
			request.getSession().setAttribute("employeeorderalreadyexists", false);
		}
	}

}
