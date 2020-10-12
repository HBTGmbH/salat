package org.tb.web.action.admin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpSession;

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
	protected boolean isContentEditable(HttpSession session, @Nonnull Employeeorder employeeorder, @Nullable Employeeordercontent eoContent) {
		if (eoContent != null) {
			Employee loginEmployee = (Employee) session.getAttribute("loginEmployee");
			if (!eoContent.getCommitted_emp() && !eoContent.getCommitted_mgmt()) {
				return true;
			} else if ((!eoContent.getCommitted_mgmt() || !eoContent.getCommitted_emp())
					&& (loginEmployee.equals(employeeorder.getEmployeecontract().getEmployee()) 
							|| loginEmployee.equals(eoContent.getContactTechHbt()))) {
				return true;
			}  else {
				return GlobalConstants.EMPLOYEE_STATUS_ADM.equals(loginEmployee.getStatus());
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
	protected void setReleaseAuthorizationInSession(HttpSession session, Employeeorder employeeorder, Employeeordercontent eoContent) {
		if (eoContent != null) {
			Employee loginEmployee = (Employee) session.getAttribute("loginEmployee");
			if (loginEmployee.equals(employeeorder.getEmployeecontract().getEmployee())) {
				session.setAttribute("releaseEmpPossible", true);
				session.setAttribute("releaseMgmtPossible", false);
			} else if (loginEmployee.equals(eoContent.getContactTechHbt())) {
				session.setAttribute("releaseMgmtPossible", true);
				session.setAttribute("releaseEmpPossible", false);
			} else if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_PV)) {
				session.setAttribute("releaseEmpPossible", false);
				session.setAttribute("releaseMgmtPossible", true);
			} else if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
				session.setAttribute("releaseEmpPossible", true);
				session.setAttribute("releaseMgmtPossible", true);
			} else {
				session.setAttribute("releaseEmpPossible", false);
				session.setAttribute("releaseMgmtPossible", false);
			}
			if (eoContent.getCommitted_emp()) {
				session.setAttribute("releaseEmpPossible", false);
			}
			if (eoContent.getCommitted_mgmt()) {
				session.setAttribute("releaseMgmtPossible", false);
			}
		} else {
			// a new one cannot be released (not stored in db yet)
			session.setAttribute("releaseEmpPossible", false);
			session.setAttribute("releaseMgmtPossible", false);
		}
	}

}
