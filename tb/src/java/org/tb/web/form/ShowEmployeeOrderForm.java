package org.tb.web.form;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

/**
 * Form for showing all employee orders.
 * Actually not used - will be needed if we want to have an editable employee orders display
 * (like the timereport daily display)
 * 
 * @author oda
 *
 */
public class ShowEmployeeOrderForm extends ActionForm {


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
