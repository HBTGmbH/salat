package org.tb.web.action.admin;

import javax.servlet.http.HttpServletRequest;

import org.tb.GlobalConstants;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Employeeordercontent;
import org.tb.web.action.LoginRequiredAction;

public abstract class EmployeeOrderContentAction extends LoginRequiredAction {

	/**
	 * Checks if the content is editable.
	 * 
	 * @param request
	 * @param employeeorder
	 * @param eoContent
	 * @return Returns true, if content is editable, false otherwise
	 */
	protected boolean isContentEditable(HttpServletRequest request,
			Employeeorder employeeorder, Employeeordercontent eoContent) {
		if (eoContent != null) {
			Employee loginEmployee = (Employee) request.getSession()
					.getAttribute("loginEmployee");
			if (!eoContent.getCommitted_emp() && !eoContent.getCommitted_mgmt()) {
				return true;
			} else if ((!eoContent.getCommitted_mgmt() || !eoContent.getCommitted_emp())
					&& (loginEmployee.equals(employeeorder.getEmployeecontract().getEmployee()) 
							|| loginEmployee.equals(eoContent.getContactTechHbt()))) {
				return true;
			}  else if (loginEmployee.getStatus().equals(
					GlobalConstants.EMPLOYEE_STATUS_ADM)) {
				return true;
			} else {
				return false;
			}
		} else {
			// new content is allways editable
			return true;
		}
	}

	/**
	 * Sets the session attributs releaseEmpPossible and releaseMgmtPossible.
	 * The values are true, if a release is posible, false otherwise.
	 * 
	 * @param request
	 * @param employeeorder
	 * @param eoContent
	 */
	protected void setReleaseAuthorizationInSession(HttpServletRequest request,
			Employeeorder employeeorder, Employeeordercontent eoContent) {
		if (eoContent != null) {
			Employee loginEmployee = (Employee) request.getSession()
					.getAttribute("loginEmployee");
			if (loginEmployee.equals(employeeorder.getEmployeecontract()
					.getEmployee())) {
				request.getSession().setAttribute("releaseEmpPossible", true);
				request.getSession().setAttribute("releaseMgmtPossible", false);
			} else if (loginEmployee.equals(eoContent.getContactTechHbt())) {
				request.getSession().setAttribute("releaseMgmtPossible", true);
				request.getSession().setAttribute("releaseEmpPossible", false);
			} else if (loginEmployee.getStatus().equals(
					GlobalConstants.EMPLOYEE_STATUS_ADM)) {
				request.getSession().setAttribute("releaseEmpPossible", true);
				request.getSession().setAttribute("releaseMgmtPossible", true);
			} else {
				request.getSession().setAttribute("releaseEmpPossible", false);
				request.getSession().setAttribute("releaseMgmtPossible", false);
			}
			if (eoContent.getCommitted_emp()) {
				request.getSession().setAttribute("releaseEmpPossible", false);
			}
			if (eoContent.getCommitted_mgmt()) {
				request.getSession().setAttribute("releaseMgmtPossible", false);
			}
		} else {
			// a new one cannot be released (not stored in db yet)
			request.getSession().setAttribute("releaseEmpPossible", false);
			request.getSession().setAttribute("releaseMgmtPossible", false);
		}
	}

}
