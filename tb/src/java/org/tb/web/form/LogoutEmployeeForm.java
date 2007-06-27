package org.tb.web.form;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

/**
 * Form for the employee logout
 * 
 * @author oda
 *
 */
public class LogoutEmployeeForm extends ActionForm {

	// dummy form - no elements yet
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 7096994213160642196L;

	@Override
	public void reset(ActionMapping mapping, HttpServletRequest request) {
		// actually, nothing to reset
	}

	@Override
	public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
		ActionErrors errors = new ActionErrors();
		
		// actually, no checks here
		return errors;
	}

}
