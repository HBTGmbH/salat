package org.tb.web.action.admin;

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
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Suborder;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddSuborderForm;

/**
 * action class for storing a suborder permanently
 * 
 * @author oda
 *
 */
public class StoreSuborderAction extends LoginRequiredAction {
	
	
	private CustomerorderDAO customerorderDAO;
	private SuborderDAO suborderDAO;
	
	
	public void setSuborderDAO(SuborderDAO suborderDAO) {
		this.suborderDAO = suborderDAO;
	}

	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}


	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
			AddSuborderForm soForm = (AddSuborderForm) form;
	
			if ((request.getParameter("task") != null)
					&& (request.getParameter("task").equals("refreshHourlyRate"))) {
				// refresh suborder default hourly rate after change of order
				// (same rate as for order itself)
				if (refreshHourlyRate(mapping, request, soForm) != true) {
					return mapping.findForward("error");
				} else {
					return mapping.getInputForward();
				}
			}
			
			if ((request.getParameter("task") != null) && 
					(request.getParameter("task").equals("save")) ||
					(request.getParameter("soId") != null)) {

				// 'main' task - prepare everything to store the suborder.
				// I.e., copy properties from the form into the suborder before saving.				
				long soId = -1;
				Suborder so = null;
				if (request.getSession().getAttribute("soId") != null) {
					// edited suborder
					soId = Long.parseLong(request.getSession().getAttribute("soId").toString());
					so = suborderDAO.getSuborderById(soId);
				} else {
					// new report
					so = new Suborder();
				}
				
				ActionMessages errorMessages = validateFormData(request, soForm);
				if (errorMessages.size() > 0) {
					return mapping.getInputForward();
				}
				
				so.setCurrency(soForm.getCurrency());
				so.setCustomerorder(customerorderDAO.getCustomerorderById(soForm.getCustomerorderId()));
				so.setSign(soForm.getSign());
				so.setDescription(soForm.getDescription());
				so.setShortdescription(soForm.getShortdescription());
				so.setHourly_rate(soForm.getHourlyRate());
				so.setInvoice(soForm.getInvoice().charAt(0));
				so.setStandard(soForm.getStandard());
				so.setCommentnecessary(soForm.getCommentnecessary());
					
				Employee loginEmployee = (Employee)request.getSession().getAttribute("loginEmployee");
				suborderDAO.save(so, loginEmployee);
				
				request.getSession().setAttribute("suborders", suborderDAO.getSubordersOrderedByCustomerorder());
				request.getSession().removeAttribute("soId");
				
				// store used customer order id for the next creation of a suborder
				request.getSession().setAttribute("lastCoId",so.getCustomerorder().getId());
				
				
				boolean addMoreSuborders = Boolean.parseBoolean((String)request.getParameter("continue"));
				if (!addMoreSuborders) {
					return mapping.findForward("success");
				} else {
					// reuse form entries and show add-page
					soForm.setDescription("");
					soForm.setSign("");
					soForm.setInvoice("J");
					soForm.setCurrency(GlobalConstants.DEFAULT_CURRENCY);
					return mapping.findForward("reset");
				}
			} 
			if ((request.getParameter("task") != null) && 
				(request.getParameter("task").equals("back"))) {	
				// go back
				request.getSession().removeAttribute("soId");
				soForm.reset(mapping, request);
				return mapping.findForward("cancel");
			} 
			if ((request.getParameter("task") != null) && 
				(request.getParameter("task").equals("reset"))) {	
				// reset form
				doResetActions(mapping, request, soForm);
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
	private void doResetActions(ActionMapping mapping, HttpServletRequest request, AddSuborderForm soForm) {
		soForm.reset(mapping, request);
	}
	
	/**
	 * validates the form data (syntax and logic)
	 * 
	 * @param request
	 * @param cuForm
	 * @return
	 */
	private ActionMessages validateFormData(HttpServletRequest request, AddSuborderForm soForm) {

		ActionMessages errors = getErrors(request);
		if(errors == null) errors = new ActionMessages();
		
		// for a new suborder, check if the sign already exists
		if (request.getSession().getAttribute("soId") == null) {
			List<Suborder> allSuborders = suborderDAO.getSuborders();
			for (Iterator iter = allSuborders.iterator(); iter.hasNext();) {
				Suborder so = (Suborder) iter.next();
				if ((so.getCustomerorder().getId() == soForm.getCustomerorderId())  && (so.getSign().equalsIgnoreCase(soForm.getSign()))) {
					errors.add("sign", new ActionMessage("form.suborder.error.sign.alreadyexists"));		
					break;
				}
			} 
		}
		
		// check length of text fields
		if (soForm.getSign().length() > GlobalConstants.CUSTOMERORDER_SIGN_MAX_LENGTH) {
			errors.add("sign", new ActionMessage("form.suborder.error.sign.toolong"));
		}
		if (soForm.getSign().length() <= 0) {
			errors.add("sign", new ActionMessage("form.suborder.error.sign.required"));
		}
		if (soForm.getDescription().length() > GlobalConstants.CUSTOMERORDER_DESCRIPTION_MAX_LENGTH) {
			errors.add("description", new ActionMessage("form.suborder.error.description.toolong"));
		}
		if (soForm.getShortdescription().length() > GlobalConstants.CUSTOMERORDER_SHORT_DESCRIPTION_MAX_LENGTH) {
			errors.add("shortdescription", new ActionMessage("form.suborder.error.shortdescription.toolong"));
		}
		if (soForm.getCurrency().length() > GlobalConstants.CUSTOMERORDER_CURRENCY_MAX_LENGTH) {
			errors.add("currency", new ActionMessage("form.suborder.error.currency.toolong"));
		}
		if (soForm.getCurrency().length() <= 0) {
			errors.add("currency", new ActionMessage("form.suborder.error.currency.required"));
		}
		
		// check invoice character
		if ((soForm.getInvoice().charAt(0) != GlobalConstants.SUBORDER_INVOICE_YES) &&
			(soForm.getInvoice().charAt(0) != GlobalConstants.SUBORDER_INVOICE_NO) &&
			(soForm.getInvoice().charAt(0) != GlobalConstants.SUBORDER_INVOICE_UNDEFINED)) {
				errors.add("invoice", new ActionMessage("form.suborder.error.invoice.invalid"));
		}
		
		// check hourly rate format		
		if (!GenericValidator.isDouble(soForm.getHourlyRate().toString()) ||
				(!GenericValidator.isInRange(soForm.getHourlyRate(), 
						0.0, GlobalConstants.MAX_HOURLY_RATE))) {
			errors.add("hourlyRate", new ActionMessage("form.suborder.error.hourlyrate.wrongformat"));
		}
		
		saveErrors(request, errors);
		
		return errors;
	}
	
	/**
	 * refreshes suborder default hourly rate after change of order
	 * (same rate as for order itself)
	 * 
	 * @param mapping
	 * @param request
	 * @param soForm
	 * @return
	 */
	private boolean refreshHourlyRate(ActionMapping mapping,
			HttpServletRequest request, AddSuborderForm soForm) {
		
		Customerorder co = customerorderDAO.getCustomerorderById(soForm.getCustomerorderId());
		
		if (co != null) {
			request.getSession().setAttribute("currentOrderId", new Long(co.getId()));
			request.getSession().setAttribute("hourlyRate", co.getHourly_rate());
			request.getSession().setAttribute("currency", co.getCurrency());
			soForm.setHourlyRate(co.getHourly_rate());
			soForm.setCurrency(co.getCurrency());
		} else {
			soForm.setHourlyRate(0.0);
			soForm.setCurrency(GlobalConstants.DEFAULT_CURRENCY);
		}

		return true;

	}
}
