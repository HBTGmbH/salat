package org.tb.web.action.admin;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.LinkedList;
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
import org.tb.bdom.CustomerOrderViewDecorator;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;
import org.tb.persistence.CustomerDAO;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.util.DateUtils;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddCustomerOrderForm;

/**
 * action class for storing a customer order permanently
 * 
 * @author oda
 *
 */
public class StoreCustomerorderAction extends LoginRequiredAction {
	
	
	private CustomerDAO customerDAO;
	private CustomerorderDAO customerorderDAO;
	private EmployeeDAO employeeDAO;
	private TimereportDAO timereportDAO;
	private SuborderDAO suborderDAO;
	
	private EmployeeorderDAO employeeorderDAO;
	
	public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
		this.employeeorderDAO = employeeorderDAO;
	}

	public void setSuborderDAO(SuborderDAO suborderDAO) {
		this.suborderDAO = suborderDAO;
	}
	
	public void setTimereportDAO(TimereportDAO timereportDAO) {
		this.timereportDAO = timereportDAO;
	}
	
	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}
	
	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}

	public void setCustomerDAO(CustomerDAO customerDAO) {
		this.customerDAO = customerDAO;
	}


	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
			AddCustomerOrderForm coForm = (AddCustomerOrderForm) form;
	
			/* remove list with timereports out of range */
			request.getSession().removeAttribute("timereportsOutOfRange");
			
			if ((request.getParameter("task") != null) && 
					(request.getParameter("task").equals("save")) ||
					(request.getParameter("coId") != null)) {
				
				ActionMessages errorMessages = validateFormData(request, coForm);
				if (errorMessages.size() > 0) {
					return mapping.getInputForward();
				}
				
				// 'main' task - prepare everything to store the customer.
				// I.e., copy properties from the form into the customer before saving.
				long coId = -1;
				Customerorder co = null;
				if (request.getSession().getAttribute("coId") != null) {
					// edited customerorder
					coId = Long.parseLong(request.getSession().getAttribute("coId").toString());
				} 
								
				
				Date untilDate;
				if (coForm.getValidUntil() != null && !coForm.getValidUntil().trim().equals("")) {
					untilDate = Date.valueOf(coForm.getValidUntil());
				} else {
					untilDate = null;
				}				
				Date fromDate = Date.valueOf(coForm.getValidFrom());
				
				
				Employee loginEmployee = (Employee)request.getSession().getAttribute("loginEmployee");
				
				/* adjust suborders */
				List<Suborder> suborders = suborderDAO.getSubordersByCustomerorderId(coId);
				if (suborders != null && !suborders.isEmpty()) {
					for (Suborder so : suborders) {
						boolean suborderchanged = false;
						if (so.getFromDate().before(fromDate)) {
							so.setFromDate(fromDate);
							suborderchanged = true;
						}
						if (so.getUntilDate() != null && so.getUntilDate().before(fromDate)) {
							 so.setUntilDate(fromDate);
							 suborderchanged = true;
						 }
						if (untilDate != null) {
							if (so.getFromDate().after(untilDate)) {
								 so.setFromDate(untilDate);
								 suborderchanged = true;
							 }
							 if (so.getUntilDate() == null || so.getUntilDate().after(untilDate)) {
								 so.setUntilDate(untilDate);
								 suborderchanged = true;
							 }
						}
						
						if (suborderchanged) {
						
							suborderDAO.save(so, loginEmployee);
							
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
						}
					}
				}
				
				if (coId != 0 && coId != -1) {
					co = customerorderDAO.getCustomerorderById(coId);
				}
				if (co == null) {
					// new customer order
					co = new Customerorder();
				}
				
				/* set attributes */				
				co.setCurrency(coForm.getCurrency());
				co.setCustomer(customerDAO.getCustomerById(coForm.getCustomerId()));
				
				co.setUntilDate(untilDate);
				co.setFromDate(fromDate);
				
				co.setSign(coForm.getSign());
				co.setDescription(coForm.getDescription());
				co.setShortdescription(coForm.getShortdescription());
				co.setHourly_rate(coForm.getHourlyRate());
				co.setOrder_customer(coForm.getOrderCustomer());
				
				co.setResponsible_customer_contractually(coForm.getResponsibleCustomerContractually());
				co.setResponsible_customer_technical(coForm.getResponsibleCustomerTechnical());
				co.setResponsible_hbt(employeeDAO.getEmployeeById(coForm.getEmployeeId()));
				co.setRespEmpHbtContract(employeeDAO.getEmployeeById(coForm.getRespContrEmployeeId()));
				
				if (coForm.getDebithours() == null || coForm.getDebithours() == 0.0) {
					co.setDebithours(null);
					co.setDebithoursunit(null);
				} else {
					co.setDebithours(coForm.getDebithours());
					co.setDebithoursunit(coForm.getDebithoursunit());
				}

				co.setStatusreport(coForm.getStatusreport());
				co.setHide(coForm.getHide());
				
				
				customerorderDAO.save(co, loginEmployee);
				
				request.getSession().setAttribute("customerorders", customerorderDAO.getCustomerorders());
				request.getSession().removeAttribute("coId");
				
				boolean addMoreOrders = Boolean.parseBoolean(request.getParameter("continue"));
				if (!addMoreOrders) {
					
					String filter = null;
					Boolean show = null;
					Long customerId = null;
					if (request.getSession().getAttribute("customerorderFilter") != null) {
						filter = (String) request.getSession().getAttribute("customerorderFilter");
					}
					if (request.getSession().getAttribute("customerorderShow") != null) {
						show = (Boolean) request.getSession().getAttribute("customerorderShow");
					}
					if (request.getSession().getAttribute("customerorderCustomerId") != null) {
						customerId = (Long) request.getSession().getAttribute("customerorderCustomerId");
					}
					
					
					boolean showActualHours = (Boolean) request.getSession().getAttribute("showActualHours");				
					if (showActualHours) {
						/* show actual hours */
						List<Customerorder> customerOrders =  customerorderDAO.getCustomerordersByFilters(show, filter, customerId);
						List<CustomerOrderViewDecorator> decorators = new LinkedList<CustomerOrderViewDecorator>();
						for (Customerorder customerorder : customerOrders) {
							CustomerOrderViewDecorator decorator = new CustomerOrderViewDecorator(timereportDAO, customerorder);
							decorators.add(decorator);
						}
						request.getSession().setAttribute("customerorders", decorators);
					} else {
						request.getSession().setAttribute("customerorders", customerorderDAO.getCustomerordersByFilters(show, filter, customerId));			

					}
					
					return mapping.findForward("success");
				} else {
					// reuse form entries and show add-page
					return mapping.findForward("reset");
				}
			} 
			if ((request.getParameter("task") != null) && 
				(request.getParameter("task").equals("back"))) {	
				// go back
				request.getSession().removeAttribute("coId");
				coForm.reset(mapping, request);
				return mapping.findForward("cancel");
			} 
			if ((request.getParameter("task") != null) && 
				(request.getParameter("task").equals("reset"))) {	
				// reset form
				doResetActions(mapping, request, coForm);
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
	private void doResetActions(ActionMapping mapping, HttpServletRequest request, AddCustomerOrderForm coForm) {
		coForm.reset(mapping, request);
	}
	
	/**
	 * validates the form data (syntax and logic)
	 * 
	 * @param request
	 * @param cuForm
	 * @return
	 */
	private ActionMessages validateFormData(HttpServletRequest request, AddCustomerOrderForm coForm) {

		ActionMessages errors = getErrors(request);
		if(errors == null) errors = new ActionMessages();
		
		//	check date formats (must now be 'yyyy-MM-dd')
		String dateFromString = coForm.getValidFrom().trim();
		boolean dateError = DateUtils.validateDate(dateFromString);
		if (dateError) {
			errors.add("validFrom", new ActionMessage("form.timereport.error.date.wrongformat"));
		} 
		
		if (coForm.getValidUntil() != null && !coForm.getValidUntil().trim().equals("")) {
			String dateUntilString = coForm.getValidUntil().trim();
			dateError = DateUtils.validateDate(dateUntilString);
			if (dateError) {
				errors.add("validUntil", new ActionMessage(
						"form.timereport.error.date.wrongformat"));
			}
		}	
		
		Long coId = (Long) request.getSession().getAttribute("coId");
		// for a new customerorder, check if the sign already exists
		if (coId == null) {
			coId = 0L;
			List<Customerorder> allCustomerorders = customerorderDAO.getCustomerorders();
			for (Iterator iter = allCustomerorders.iterator(); iter.hasNext();) {
				Customerorder co = (Customerorder) iter.next();
				if (co.getSign().equalsIgnoreCase(coForm.getSign())) {
					errors.add("sign", new ActionMessage("form.customerorder.error.sign.alreadyexists"));		
					break;
				}
			}
		}
		
		// check length of text fields and if they are filled
		if (coForm.getSign().length() > GlobalConstants.CUSTOMERORDER_SIGN_MAX_LENGTH) {
			errors.add("sign", new ActionMessage("form.customerorder.error.sign.toolong"));
		}
		if (coForm.getSign().length() <= 0) {
			errors.add("sign", new ActionMessage("form.customerorder.error.sign.required"));
		}
		if (coForm.getDescription().length() > GlobalConstants.CUSTOMERORDER_DESCRIPTION_MAX_LENGTH) {
			errors.add("description", new ActionMessage("form.customerorder.error.description.toolong"));
		}
		if ("".equals(coForm.getDescription().trim())) {
			errors.add("description", new ActionMessage("form.error.description.necessary"));
		}
		if (coForm.getShortdescription().length() > GlobalConstants.CUSTOMERORDER_SHORT_DESCRIPTION_MAX_LENGTH) {
			errors.add("shortdescription", new ActionMessage("form.customerorder.error.shortdescription.toolong"));
		}
//		if (coForm.getCurrency().length() > GlobalConstants.CUSTOMERORDER_CURRENCY_MAX_LENGTH) {
//			errors.add("currency", new ActionMessage("form.customerorder.error.currency.toolong"));
//		}
//		if (coForm.getCurrency().length() <= 0) {
//			errors.add("currency", new ActionMessage("form.customerorder.error.currency.required"));
//		}
		if (coForm.getOrderCustomer().length() > GlobalConstants.CUSTOMERORDER_ORDER_CUSTOMER_MAX_LENGTH) {
			errors.add("orderCustomer", new ActionMessage("form.customerorder.error.ordercustomer.toolong"));
		}
		if (coForm.getOrderCustomer().length() <= 0) {
			coForm.setOrderCustomer("-");
		}
		if (coForm.getResponsibleCustomerContractually().length() > GlobalConstants.CUSTOMERORDER_RESP_CUSTOMER_MAX_LENGTH) {
			errors.add("responsibleCustomerContractually", new ActionMessage("form.customerorder.error.responsiblecustomer.toolong"));
		}
		if (coForm.getResponsibleCustomerContractually().length() <= 0) {
			errors.add("responsibleCustomerContractually", new ActionMessage("form.customerorder.error.responsiblecustomer.required"));
		}
		if (coForm.getResponsibleCustomerTechnical().length() > GlobalConstants.CUSTOMERORDER_RESP_CUSTOMER_MAX_LENGTH) {
			errors.add("responsibleCustomerTechnical", new ActionMessage("form.customerorder.error.responsiblecustomer.toolong"));
		}
		if (coForm.getResponsibleCustomerTechnical().length() <= 0) {
			errors.add("responsibleCustomerTechnical", new ActionMessage("form.customerorder.error.responsiblecustomer.required"));
		}
				
		
		// check hourly rate format		
		if (!GenericValidator.isDouble(coForm.getHourlyRate().toString()) ||
				(!GenericValidator.isInRange(coForm.getHourlyRate(), 
						0.0, GlobalConstants.MAX_HOURLY_RATE))) {
			errors.add("hourlyRate", new ActionMessage("form.customerorder.error.hourlyrate.wrongformat"));
		}
		
		if (!GenericValidator.isDouble(coForm.getDebithours().toString()) ||
				(!GenericValidator.isInRange(coForm.getDebithours(), 
						0.0, GlobalConstants.MAX_DEBITHOURS))) {
			errors.add("debithours", new ActionMessage("form.customerorder.error.debithours.wrongformat"));
		} else if (coForm.getDebithours() != null && coForm.getDebithours() != 0.0) {
			Double debithours = coForm.getDebithours() * 100000;
			debithours += 0.5;
			
			int debithours2 = debithours.intValue();
			int modulo = debithours2%5000;
			coForm.setDebithours(debithours2/100000.0);
			
			if (modulo != 0) {
				errors.add("debithours", new ActionMessage("form.customerorder.error.debithours.wrongformat2"));
			}
		} 
		
		if (coForm.getDebithours() != 0.0) {
			if (coForm.getDebithoursunit() == null || !(coForm.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_MONTH ||
					coForm.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_YEAR || coForm.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_TOTALTIME)) {
				errors.add("debithours", new ActionMessage("form.customerorder.error.debithours.nounit"));
			}
		}
		
//		 check, if dates fit to existing timereports
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		Date fromDate = null;
		Date untilDate = null;
		try {
			fromDate = new Date(simpleDateFormat.parse(coForm.getValidFrom().trim()).getTime());
			untilDate = new Date(simpleDateFormat.parse(coForm.getValidUntil().trim()).getTime());
		} catch (Exception e) {
			// do nothing
		}
		List<Timereport> timereportsInvalidForDates = timereportDAO.
			getTimereportsByCustomerOrderIdInvalidForDates(fromDate, untilDate, coId);
		if (timereportsInvalidForDates != null && !timereportsInvalidForDates.isEmpty()) {
			request.getSession().setAttribute("timereportsOutOfRange", timereportsInvalidForDates);
			errors.add("timereportOutOfRange", new ActionMessage("form.general.error.timereportoutofrange"));
			
		}
		
		saveErrors(request, errors);
		
		return errors;
	}
}
