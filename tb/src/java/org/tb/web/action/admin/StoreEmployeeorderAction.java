package org.tb.web.action.admin;

import java.sql.Date;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.tb.GlobalConstants;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.helper.EmployeeHelper;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.util.DateUtils;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddEmployeeOrderForm;

/**
 * action class for storing an employee order permanently
 * 
 * @author oda
 *
 */
public class StoreEmployeeorderAction extends LoginRequiredAction {
	
	
	private EmployeeDAO employeeDAO;
	private EmployeecontractDAO employeecontractDAO;
	private EmployeeorderDAO employeeorderDAO;
	private SuborderDAO suborderDAO;
	
	
	public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
		this.employeeorderDAO = employeeorderDAO;
	}

	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
		this.employeecontractDAO = employeecontractDAO;
	}

	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}

	public void setSuborderDAO(SuborderDAO suborderDAO) {
		this.suborderDAO = suborderDAO;
	}

	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
			AddEmployeeOrderForm eoForm = (AddEmployeeOrderForm) form;
	
			if ((request.getParameter("task") != null) && 
					(request.getParameter("task").equals("save")) ||
					(request.getParameter("eoId") != null)) {

				// 'main' task - prepare everything to store the employee order.
				// I.e., copy properties from the form into the employee order before saving.						
				long eoId = -1;
				Employeeorder eo = null;
				if (request.getSession().getAttribute("eoId") != null) {
					// edited employeeorder
					eoId = Long.parseLong(request.getSession().getAttribute("eoId").toString());
					eo = employeeorderDAO.getEmployeeorderById(eoId);
				} else {
					// new report
					eo = new Employeeorder();
				}
				
				ActionMessages errorMessages = validateFormData(request, eoForm);
				if (errorMessages.size() > 0) {
					return mapping.getInputForward();
				}
				
				EmployeeHelper eh = new EmployeeHelper();
				String[] firstAndLast = eh.splitEmployeename(eoForm.getEmployeename());
				Employeecontract ec = employeecontractDAO.getEmployeeContractByEmployeeName(firstAndLast[0], firstAndLast[1]);
				eo.setEmployeecontract(ec);
				eo.setSuborder(suborderDAO.getSuborderById(eoForm.getSuborderId()));
				
				Date fromDate = Date.valueOf(eoForm.getValidFrom());
				Date untilDate = Date.valueOf(eoForm.getValidUntil());
				eo.setFromDate(fromDate);
				eo.setUntilDate(untilDate);
				eo.setSign(eoForm.getSign());
				eo.setStatus(eoForm.getStatus());
				eo.setStandingorder(eoForm.getStandingorder());
				eo.setDebithours(eoForm.getDebithours());
				eo.setStatusreport(eoForm.getStatusreport());

				employeeorderDAO.save(eo);
				
				List<Employee> employeeOptionList = employeeDAO.getEmployees();
				request.getSession().setAttribute("employees", employeeOptionList);
				
				request.getSession().setAttribute("employeeorders", employeeorderDAO.getEmployeeorders());
				request.getSession().removeAttribute("eoId");
				return mapping.findForward("success");
			} 
			if ((request.getParameter("task") != null) && 
				(request.getParameter("task").equals("back"))) {	
				// go back
				request.getSession().removeAttribute("eoId");
				eoForm.reset(mapping, request);
				return mapping.findForward("cancel");
			} 
			if ((request.getParameter("task") != null) && 
				(request.getParameter("task").equals("reset"))) {	
				// reset form
				doResetActions(mapping, request, eoForm);
				return mapping.getInputForward();				
			}	
						
			return mapping.findForward("error");
			
	}
	
	/**
	 * resets the 'add report' form to default values
	 * 
	 * @param mapping
	 * @param request
	 * @param reportForm
	 */
	private void doResetActions(ActionMapping mapping, HttpServletRequest request, AddEmployeeOrderForm eoForm) {
		eoForm.reset(mapping, request);
	}
	
	/**
	 * validates the form data (syntax and logic)
	 * 
	 * @param request
	 * @param cuForm
	 * @return
	 */
	private ActionMessages validateFormData(HttpServletRequest request, AddEmployeeOrderForm eoForm) {

		ActionMessages errors = getErrors(request);
		if(errors == null) errors = new ActionMessages();
		
		//	check date formats (must now be 'yyyy-MM-dd')
		String dateFromString = eoForm.getValidFrom().trim();
		boolean dateError = DateUtils.validateDate(dateFromString);
		if (dateError) {
			errors.add("validFrom", new ActionMessage("form.timereport.error.date.wrongformat"));
		} 
		
		String dateUntilString = eoForm.getValidUntil().trim();
		dateError = DateUtils.validateDate(dateUntilString);
		if (dateError) {
			errors.add("validUntil", new ActionMessage("form.timereport.error.date.wrongformat"));
		} 
		
		// for a new employeeorder, check if the sign already exists
		if (request.getSession().getAttribute("eoId") == null) {
			List<Employeeorder> allEmployeeorders = employeeorderDAO.getEmployeeorders();
			for (Iterator iter = allEmployeeorders.iterator(); iter.hasNext();) {
				Employeeorder eo = (Employeeorder) iter.next();
				if (eo.getSign().equalsIgnoreCase(eoForm.getSign())) {
					errors.add("sign", new ActionMessage("form.employeeorder.error.sign.alreadyexists"));		
					break;
				}
			}
		}
		
		// for a new employeeorder, check if the suborder together with employee already exists
		if (request.getSession().getAttribute("eoId") == null) {
			List<Employeeorder> allEmployeeorders = employeeorderDAO.getEmployeeorders();
			Suborder soInForm = suborderDAO.getSuborderById(eoForm.getSuborderId());
			for (Iterator iter = allEmployeeorders.iterator(); iter.hasNext();) {
				Employeeorder eo = (Employeeorder) iter.next();
				if ((eo.getSuborder().getSign().equalsIgnoreCase(soInForm.getSign())) &&
					(eo.getEmployeecontract().getEmployee().getName().equalsIgnoreCase(eoForm.getEmployeename()))) {
					errors.add("suborderId", new ActionMessage("form.employeeorder.error.employeesuborder.alreadyexist"));		
					break;
				}
			}
		}
		
		// check if valid suborder exists - otherwise, no save possible
		if (eoForm.getSuborderId() <= 0) {
			errors.add("suborderId", new ActionMessage("form.employeeorder.suborder.invalid"));
		}
		
		// check length of text fields and if they are filled
		if (eoForm.getSign().length() > GlobalConstants.EMPLOYEEORDER_SIGN_MAX_LENGTH) {
			errors.add("sign", new ActionMessage("form.employeeorder.error.sign.toolong"));
		}
		if (eoForm.getSign().length() <= 0) {
			errors.add("sign", new ActionMessage("form.employeeorder.error.sign.required"));
		}
		
		if (eoForm.getStatus().length() > GlobalConstants.EMPLOYEEORDER_STATUS_MAX_LENGTH) {
			errors.add("status", new ActionMessage("form.employeeorder.error.status.toolong"));
		}
		if (eoForm.getStatus().length() <= 0) {
			errors.add("status", new ActionMessage("form.employeeorder.error.status.required"));
		}
		
		// check debit hours format		
		if (!GenericValidator.isDouble(eoForm.getDebithours().toString()) ||
				(!GenericValidator.isInRange(eoForm.getDebithours(), 
						0.0, GlobalConstants.MAX_DEBITHOURS))) {
			errors.add("debithours", new ActionMessage("form.employeeorder.error.debithours.wrongformat"));
		}
		
		saveErrors(request, errors);
		
		return errors;
	}
}
