package org.tb.web.action.admin;

import java.sql.Date;
import java.text.SimpleDateFormat;
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
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;
import org.tb.logging.TbLogger;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
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
	
	private TimereportDAO timereportDAO;
	
	private EmployeeorderDAO employeeorderDAO;
	
	public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
		this.employeeorderDAO = employeeorderDAO;
	}
	
	public void setTimereportDAO(TimereportDAO timereportDAO) {
		this.timereportDAO = timereportDAO;
	}

	public void setSuborderDAO(SuborderDAO suborderDAO) {
		this.suborderDAO = suborderDAO;
	}

	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}

	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping,
			ActionForm form, HttpServletRequest request,
			HttpServletResponse response) {
		AddSuborderForm soForm = (AddSuborderForm) form;
		
//		 remove list with timereports out of range
		request.getSession().removeAttribute("timereportsOutOfRange");

		if ((request.getParameter("task") != null)
				&& (request.getParameter("task").equals("generateSign"))) {
			
			Suborder tempSubOrder = suborderDAO.getSuborderById(soForm.getParentId());
			Customerorder tempOrder = customerorderDAO.getCustomerorderById(soForm.getParentId());
			List<Suborder> suborders = suborderDAO.getSuborders();
			TbLogger.getLogger().debug(" StoreSuborderAction.executeAuthenticated()  -  3 Values:  " 
					+ tempSubOrder  + " / " +  tempOrder + " / " + suborders);
			Long soId = new Long(-1);
				try{
					soId = new Long(request.getSession().getAttribute("soId").toString());
				}catch(Throwable th){}
			if (suborders != null){
				if (tempSubOrder != null){
					int version = 1;
					for (int i =0; i<suborders.size();i++){
						if (suborders.get(i).getParentorder()!=null 
								&& suborders.get(i).getParentorder().getId() == tempSubOrder.getId()){
							if (suborders.get(i).getSign().equals( tempSubOrder.getSign() + "." + version)
									&& !(soId==suborders.get(i).getId())) {
								version++;
							}
						}
					}
					soForm.setSign(tempSubOrder.getSign() + "." + version );
				}else{
					
					if (tempOrder != null){
						int version = 1;
						for (int i =0; i<suborders.size();i++){
							if (suborders.get(i).getParentorder()==null
									&& suborders.get(i).getCustomerorder().getId() == tempOrder.getId()){
								if (suborders.get(i).getSign().equals(tempOrder.getSign() + "." + version)
										&& !(soId==suborders.get(i).getId())) {
									version++;
								} 
							}
						}
						soForm.setSign(tempOrder.getSign() + "." + version );
					}
				}
			}
			return mapping.getInputForward();
		}		
		
		if ((request.getParameter("task") != null)
				&& (request.getParameter("task").equals("refreshParentProject"))) {
			
			soForm.setParentDescriptionAndSign(customerorderDAO.getCustomerorderById(soForm
					.getCustomerorderId()).getSignAndDescription());
			soForm.setParentId(soForm.getCustomerorderId());
			request.getSession().setAttribute("parentDescriptionAndSign", soForm.getParentDescriptionAndSign());
			
			if ((request.getParameter("continue") != null)) {
				try{
					soForm.setParentId(Long.parseLong(request.getParameter("continue")));
					Suborder tempSubOrder = suborderDAO.getSuborderById(soForm.getParentId());
					if (tempSubOrder!=null){
						soForm.setParentDescriptionAndSign(tempSubOrder.getSignAndDescription());
					}else{
						Customerorder tempOrder = customerorderDAO.getCustomerorderById(soForm.getParentId());
						soForm.setParentDescriptionAndSign(tempOrder.getSignAndDescription());
					}
					request.getSession().setAttribute("parentDescriptionAndSign", soForm.getParentDescriptionAndSign());
				}catch(Throwable th){
					return mapping.findForward("error");
				}
			}
			//request.getSession().setAttribute("currentSuborderID", new Long(soForm.getId()));
			
			return mapping.getInputForward();	
		}
		

		if ((request.getParameter("task") != null)
				&& (request.getParameter("task").equals("refreshHourlyRate"))) {
			//first refresh the treestructure-content
			soForm.setParentDescriptionAndSign(customerorderDAO.getCustomerorderById(soForm
					.getCustomerorderId()).getSignAndDescription());
			soForm.setParentId(soForm.getCustomerorderId());
			request.getSession().setAttribute("parentDescriptionAndSign", soForm.getParentDescriptionAndSign());
			
			// refresh suborder default hourly rate after change of order
			// (same rate as for order itself)
			if (refreshHourlyRate(mapping, request, soForm) != true) {
				return mapping.findForward("error");
			} else {
				return mapping.getInputForward();
			}
		}

		if ((request.getParameter("task") != null)
				&& (request.getParameter("task").equals("save"))
				|| (request.getParameter("soId") != null)) {

			// 'main' task - prepare everything to store the suborder.
			// I.e., copy properties from the form into the suborder before
			// saving.
			long soId = -1;
			Suborder so = null;
			if (request.getSession().getAttribute("soId") != null) {
				// edited suborder
				soId = Long.parseLong(request.getSession().getAttribute("soId")
						.toString());
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
			so.setCustomerorder(customerorderDAO.getCustomerorderById(soForm
					.getCustomerorderId()));
			so.setSign(soForm.getSign());
			so.setDescription(soForm.getDescription());
			so.setShortdescription(soForm.getShortdescription());
			so.setHourly_rate(soForm.getHourlyRate());
			so.setInvoice(soForm.getInvoice().charAt(0));
			so.setStandard(soForm.getStandard());
			so.setCommentnecessary(soForm.getCommentnecessary());
			
			
			if (soForm.getValidFrom() != null && !soForm.getValidFrom().trim().equals("")) {
				Date fromDate = Date.valueOf(soForm.getValidFrom());
				so.setFromDate(fromDate);
			} else {
				so.setFromDate(so.getCustomerorder().getFromDate());
			}
			if (soForm.getValidUntil() != null && !soForm.getValidUntil().trim().equals("")) {
				Date untilDate = Date.valueOf(soForm.getValidUntil());
				so.setUntilDate(untilDate);
			} else {
				so.setUntilDate(null);
			}
			
			Employee loginEmployee = (Employee) request.getSession()
			.getAttribute("loginEmployee");
			
			// adjust employeeorders
			List<Employeeorder> employeeorders = employeeorderDAO.getEmployeeOrdersBySuborderId(so.getId());
			if (employeeorders != null && !employeeorders.isEmpty()) {
				for (Employeeorder employeeorder : employeeorders) {
					boolean changed = false;
					 if (employeeorder.getFromDate().before(so.getFromDate())) {
						 employeeorder.setFromDate(so.getFromDate());
						 changed = true;
					 }
					 if (employeeorder.getUntilDate() != null && employeeorder.getUntilDate().before(so.getFromDate())) {
						 employeeorder.setUntilDate(so.getFromDate());
						 changed = true;
					 }
					 if (so.getUntilDate() != null) {
						 if (employeeorder.getFromDate().after(so.getUntilDate())) {
							 employeeorder.setFromDate(so.getUntilDate());
							 changed = true;
						 }
						 if (employeeorder.getUntilDate() == null || employeeorder.getUntilDate().after(so.getUntilDate())) {
							 employeeorder.setUntilDate(so.getUntilDate());
							 changed = true;
						 }
					 }
					if (changed) {
						employeeorderDAO.save(employeeorder, loginEmployee);
					}						 
				}
			}
	
			
			if (soForm.getDebithours() == null || soForm.getDebithours() == 0.0) {
				so.setDebithours(null);
				so.setDebithoursunit(null);
			} else {
				so.setDebithours(soForm.getDebithours());
				so.setDebithoursunit(soForm.getDebithoursunit());
			}
			so.setHide(soForm.getHide());
			
			so.setParentorder(suborderDAO.getSuborderById(soForm.getParentId()));
			
			suborderDAO.save(so, loginEmployee);

//			String filter = (String) request.getSession().getAttribute(
//					"suborderFilter");
//			if (filter != null && !filter.equalsIgnoreCase("")) {
//				request.getSession().setAttribute("suborders",
//						suborderDAO.getSubordersByFilter(filter));
//			} else {
//				request.getSession().setAttribute("suborders",
//						suborderDAO.getSubordersOrderedByCustomerorder());
//			}
			request.getSession().removeAttribute("soId");

			// store used customer order id for the next creation of a suborder
			request.getSession().setAttribute("lastCoId",
					so.getCustomerorder().getId());

			boolean addMoreSuborders = Boolean.parseBoolean((String) request
					.getParameter("continue"));
			if (!addMoreSuborders) {
				String filter = null;
				Boolean show = null;
				Long customerOrderId = null; 
				if (request.getSession().getAttribute("suborderFilter") != null) {
					filter = (String) request.getSession().getAttribute("suborderFilter");
				}
				if (request.getSession().getAttribute("suborderShow") != null) {
					show = (Boolean) request.getSession().getAttribute("suborderShow");
				}
				if (request.getSession().getAttribute("suborderCustomerOrderId") != null) {
					customerOrderId = (Long) request.getSession().getAttribute("suborderCustomerOrderId");
				}
				request.getSession().setAttribute("suborders", suborderDAO.getSubordersByFilters(show, filter, customerOrderId));
				
				return mapping.findForward("success");
			} else {
				request.getSession().setAttribute("suborders", suborderDAO.getSuborders());
				// reuse form entries and show add-page
				soForm.setDescription("");
				soForm.setSign("");
				soForm.setInvoice("J");
				soForm.setCurrency(GlobalConstants.DEFAULT_CURRENCY);
				return mapping.findForward("reset");
			}
		}
		if ((request.getParameter("task") != null)
				&& (request.getParameter("task").equals("back"))) {
			// go back
			request.getSession().removeAttribute("soId");
			soForm.reset(mapping, request);
			return mapping.findForward("cancel");
		}
		if ((request.getParameter("task") != null)
				&& (request.getParameter("task").equals("reset"))) {
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
	private void doResetActions(ActionMapping mapping,
			HttpServletRequest request, AddSuborderForm soForm) {
		soForm.reset(mapping, request);
	}

	/**
	 * validates the form data (syntax and logic)
	 * 
	 * @param request
	 * @param cuForm
	 * @return
	 */
	private ActionMessages validateFormData(HttpServletRequest request,
			AddSuborderForm soForm) {

		ActionMessages errors = getErrors(request);
		if (errors == null)
			errors = new ActionMessages();

		Long soId = (Long) request.getSession().getAttribute("soId");
		
		// for a new suborder, check if the sign already exists
		if (soId == null) {
			soId = 0L;
			List<Suborder> allSuborders = suborderDAO.getSuborders();
			for (Iterator iter = allSuborders.iterator(); iter.hasNext();) {
				Suborder so = (Suborder) iter.next();
				if ((so.getCustomerorder().getId() == soForm
						.getCustomerorderId())
						&& (so.getSign().equalsIgnoreCase(soForm.getSign()))) {
					errors.add("sign", new ActionMessage(
							"form.suborder.error.sign.alreadyexists"));
					break;
				}
			}
		}

		// check length of text fields
		if (soForm.getSign().length() > GlobalConstants.CUSTOMERORDER_SIGN_MAX_LENGTH) {
			errors.add("sign", new ActionMessage(
					"form.suborder.error.sign.toolong"));
		}
		if (soForm.getSign().length() <= 0) {
			errors.add("sign", new ActionMessage(
					"form.suborder.error.sign.required"));
		}
		if (soForm.getDescription().length() > GlobalConstants.CUSTOMERORDER_DESCRIPTION_MAX_LENGTH) {
			errors.add("description", new ActionMessage(
					"form.suborder.error.description.toolong"));
		}
		if ("".equals(soForm.getDescription().trim())) {
			errors.add("description", new ActionMessage("form.error.description.necessary"));
		}
		if (soForm.getShortdescription().length() > GlobalConstants.CUSTOMERORDER_SHORT_DESCRIPTION_MAX_LENGTH) {
			errors.add("shortdescription", new ActionMessage(
					"form.suborder.error.shortdescription.toolong"));
		}
		if (soForm.getCurrency().length() > GlobalConstants.CUSTOMERORDER_CURRENCY_MAX_LENGTH) {
			errors.add("currency", new ActionMessage(
					"form.suborder.error.currency.toolong"));
		}
		if (soForm.getCurrency().length() <= 0) {
			errors.add("currency", new ActionMessage(
					"form.suborder.error.currency.required"));
		}

		// check invoice character
		if ((soForm.getInvoice().charAt(0) != GlobalConstants.SUBORDER_INVOICE_YES)
				&& (soForm.getInvoice().charAt(0) != GlobalConstants.SUBORDER_INVOICE_NO)
				&& (soForm.getInvoice().charAt(0) != GlobalConstants.SUBORDER_INVOICE_UNDEFINED)) {
			errors.add("invoice", new ActionMessage(
					"form.suborder.error.invoice.invalid"));
		}

		// check hourly rate format
		if (!GenericValidator.isDouble(soForm.getHourlyRate().toString())
				|| (!GenericValidator.isInRange(soForm.getHourlyRate(), 0.0,
						GlobalConstants.MAX_HOURLY_RATE))) {
			errors.add("hourlyRate", new ActionMessage(
					"form.suborder.error.hourlyrate.wrongformat"));
		}

//		check date formats (must now be 'yyyy-MM-dd')
		Date soFromDate = null;
		Date soUntilDate = null;
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyy-MM-dd");
		
		String dateFromString = soForm.getValidFrom().trim();
		
		try {
			soFromDate = new java.sql.Date(simpleDateFormat.parse(dateFromString).getTime());
		} catch (Exception e) {
			errors.add("validFrom", new ActionMessage("form.timereport.error.date.wrongformat"));
		}
		if (soForm.getValidUntil() != null && !soForm.getValidUntil().trim().equals("")) {
			String dateUntilString = soForm.getValidUntil().trim();
			try {
				soUntilDate = new java.sql.Date(simpleDateFormat.parse(dateUntilString).getTime());
			} catch (Exception e) {
				errors.add("validUntil", new ActionMessage("form.timereport.error.date.wrongformat"));
			}
		}
		
		if (soFromDate != null && soUntilDate != null) {
			if (soUntilDate.before(soFromDate)) {
				errors.add("validUntil", new ActionMessage("form.suborder.error.date.untilbeforefrom"));
			}
		}
		
		
		// check debit hours
		if (!GenericValidator.isDouble(soForm.getDebithours().toString()) ||
				(!GenericValidator.isInRange(soForm.getDebithours(), 
						0.0, GlobalConstants.MAX_DEBITHOURS))) {
			errors.add("debithours", new ActionMessage("form.customerorder.error.debithours.wrongformat"));
		} else if (soForm.getDebithours() != null && soForm.getDebithours() != 0.0) {
			Double debithours = soForm.getDebithours() * 100000;
			debithours += 0.5;
			
			int debithours2 = debithours.intValue();
			int modulo = debithours2%5000;
			soForm.setDebithours(debithours2/100000.0);
			
			if (modulo != 0) {
				errors.add("debithours", new ActionMessage("form.customerorder.error.debithours.wrongformat2"));
			}
		} 
		
		if (soForm.getDebithours() != 0.0) {
			if (soForm.getDebithoursunit() == null || !(soForm.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_MONTH ||
					soForm.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_YEAR || soForm.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_TOTALTIME)) {
				errors.add("debithours", new ActionMessage("form.customerorder.error.debithours.nounit"));
			}
		}
		
		// check customer order
		Customerorder customerorder = customerorderDAO.getCustomerorderById(soForm.getCustomerorderId());
		if (customerorder == null) {
			errors.add("customerorder", new ActionMessage("form.suborder.error.customerorder.notfound"));
		} else {
			// check validity period
			Date coFromDate = customerorder.getFromDate();
			Date coUntilDate = customerorder.getUntilDate();
			if (soFromDate != null && coFromDate != null) {
				if (soFromDate.before(coFromDate)) {
					errors.add("validFrom", new ActionMessage("form.suborder.error.date.outofrange"));
				}
				if (!(coUntilDate == null || (soUntilDate != null && !soUntilDate.after(coUntilDate)))) {
					errors.add("validUntil", new ActionMessage("form.suborder.error.date.outofrange"));
				}
			}
			
//			// check debit hours
//			if (customerorder.getDebithours() != null && customerorder.getDebithours() != 0.0 && 
//					"Y".equals(soForm.getInvoice()) && soForm.getDebithoursunit() != null) {
//				int coDebithoursunit = customerorder.getDebithoursunit();
//				int soDebithoursunit = soForm.getDebithoursunit();
//				
//				switch (coDebithoursunit) {
//				case GlobalConstants.DEBITHOURS_UNIT_TOTALTIME:
//					switch (soDebithoursunit) {
//					case GlobalConstants.DEBITHOURS_UNIT_TOTALTIME:			
//						
//						break;
//						
//					case GlobalConstants.DEBITHOURS_UNIT_YEAR:
//						
//						break;
//					
//					case GlobalConstants.DEBITHOURS_UNIT_MONTH:
//						
//						break;	
//						
//					default:
//						throw new RuntimeException("Suborder has an invaild debit hours unit");
//					}
//					break;
//					
//				case GlobalConstants.DEBITHOURS_UNIT_YEAR:
//					switch (soDebithoursunit) {
//					case GlobalConstants.DEBITHOURS_UNIT_TOTALTIME:
//						
//						break;
//						
//					case GlobalConstants.DEBITHOURS_UNIT_YEAR:
//						
//						break;
//					
//					case GlobalConstants.DEBITHOURS_UNIT_MONTH:
//						
//						break;	
//						
//					default:
//						throw new RuntimeException("Suborder has an invaild debit hours unit");
//					}
//					break;
//				
//				case GlobalConstants.DEBITHOURS_UNIT_MONTH:
//					switch (soDebithoursunit) {
//					case GlobalConstants.DEBITHOURS_UNIT_TOTALTIME:
//						
//						break;
//						
//					case GlobalConstants.DEBITHOURS_UNIT_YEAR:
//						
//						break;
//					
//					case GlobalConstants.DEBITHOURS_UNIT_MONTH:
//						
//						break;	
//						
//					default:
//						throw new RuntimeException("Suborder has an invaild debit hours unit");
//					}
//					break;	
//					
//				default:
//					throw new RuntimeException("Customerorder has an invaild debit hours unit");
//				}
//				
//			}
			
		}
		
//		// check if billable suborder has assigned debit hours
//		if ("Y".equals(soForm.getInvoice()) && (soForm.getDebithours() == null || soForm.getDebithours() == 0.0)) {
//			errors.add("debithours", new ActionMessage("form.suborder.error.debithours.necessary"));
//		}
		
		// check if billable suborder has assigned hourly rate
		if ("Y".equals(soForm.getInvoice()) && (soForm.getHourlyRate() == null || soForm.getHourlyRate() == 0.0)) {
			errors.add("hourlyRate", new ActionMessage("form.suborder.error.hourlyrate.unavailable"));
		}
		
//		 check, if dates fit to existing timereports
		List<Timereport> timereportsInvalidForDates = timereportDAO.
			getTimereportsBySuborderIdInvalidForDates(soFromDate, soUntilDate, soId);
		if (timereportsInvalidForDates != null && !timereportsInvalidForDates.isEmpty()) {
			request.getSession().setAttribute("timereportsOutOfRange", timereportsInvalidForDates);
			errors.add("timereportOutOfRange", new ActionMessage("form.general.error.timereportoutofrange"));
			
		}
		
		
		saveErrors(request, errors);

		return errors;
	}

	/**
	 * refreshes suborder default hourly rate after change of order (same rate
	 * as for order itself)
	 * 
	 * @param mapping
	 * @param request
	 * @param soForm
	 * @return
	 */
	private boolean refreshHourlyRate(ActionMapping mapping,
			HttpServletRequest request, AddSuborderForm soForm) {

		Customerorder co = customerorderDAO.getCustomerorderById(soForm
				.getCustomerorderId());

		if (co != null) {
			request.getSession().setAttribute("currentOrderId",
					new Long(co.getId()));
			request.getSession().setAttribute("currentOrder", co);
			request.getSession()
					.setAttribute("hourlyRate", co.getHourly_rate());
			request.getSession().setAttribute("currency", co.getCurrency());
			soForm.setHourlyRate(co.getHourly_rate());
			soForm.setCurrency(co.getCurrency());

			SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
					"yyyy-MM-dd");
			soForm.setValidFrom(simpleDateFormat.format(co.getFromDate()));
			if (co.getUntilDate() != null) {
				soForm
						.setValidUntil(simpleDateFormat.format(co
								.getUntilDate()));
			} else {
				soForm.setValidUntil("");
			}
			soForm.setHide(co.getHide());

		} else {
			soForm.setHourlyRate(0.0);
			soForm.setCurrency(GlobalConstants.DEFAULT_CURRENCY);
		}

		return true;

	}
}
