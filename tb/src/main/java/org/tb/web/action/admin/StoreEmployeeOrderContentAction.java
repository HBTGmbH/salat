package org.tb.web.action.admin;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.tb.GlobalConstants;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Employeeordercontent;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeeOrderContentDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.web.form.AddEmployeeOrderContentForm;

public class StoreEmployeeOrderContentAction extends EmployeeOrderContentAction {

	private EmployeeOrderContentDAO employeeOrderContentDAO;
	private EmployeeDAO employeeDAO;
	private EmployeeorderDAO employeeorderDAO;

	@Override
	protected ActionForward executeAuthenticated(final ActionMapping mapping,
			final ActionForm form, final HttpServletRequest request,
			final HttpServletResponse response) throws Exception {

		// boolean to trigger back action
		boolean backAction = false;

		final AddEmployeeOrderContentForm contentForm = (AddEmployeeOrderContentForm) form;
		final Employee loginEmployee = (Employee) request.getSession()
				.getAttribute("loginEmployee");

		Employeeordercontent eoContent = null;

		// existing or new eoContent
		if (request.getSession().getAttribute("eoContent") != null) {
			eoContent = (Employeeordercontent) request.getSession()
					.getAttribute("eoContent");
		}

		// get the same eoContent fresh from db
		if (eoContent != null) {
			eoContent = employeeOrderContentDAO
					.getEmployeeOrderContentById(eoContent.getId());
		}

		// action save
		if ((request.getParameter("action") != null)
				&& (request.getParameter("action").equals("save"))) {

			Employeeorder employeeorder = null;

			// validate
			final ActionMessages errorMessages = validateFormData(request,
					contentForm);
			if (errorMessages.size() > 0) {
				return mapping.getInputForward();
			}

			// test, if login user is authorized to safe
			boolean authorized = false;
			if (eoContent == null) {
				eoContent = new Employeeordercontent();
				authorized = true;
			} else {
				employeeorder = employeeorderDAO
						.getEmployeeOrderByContentId(eoContent.getId());
				if (employeeorder == null) {
					request
							.setAttribute("errorMessage",
									"Associated employee order not found - please call system administrator.");
					return mapping.findForward("error");
				}
				final Employee empFromEO = employeeorder.getEmployeecontract()
						.getEmployee();
				if (!eoContent.getCommitted_emp()
						&& !eoContent.getCommitted_mgmt()) {
					// no one has committed yet - everyone may save
					authorized = true;
				} else if (empFromEO != null
						&& empFromEO.equals(loginEmployee)
						&& (!eoContent.getCommitted_emp() || !eoContent
								.getCommitted_mgmt())) {
					// only one side hast committed - emp from emploeeorder may
					// save
					authorized = true;
				} else if (loginEmployee.equals(eoContent.getContactTechHbt())
						&& (!eoContent.getCommitted_emp() || !eoContent
								.getCommitted_mgmt())) {
					// only one side has committed - bl and pv may save
					authorized = true;
				} else if (loginEmployee.getStatus().equals(
						GlobalConstants.EMPLOYEE_STATUS_ADM)) {
					// login employee is admin - admin may save
					authorized = true;
				}
			}
			if (!authorized) {
				request
						.getSession()
						.setAttribute(
								"actionInfo",
								getResources(request)
										.getMessage(getLocale(request),
												"employeeordercontent.actioninfo.notauthorized.text"));
				return mapping.findForward("success");
			}

			// store
			eoContent.setAdditional_risks(contentForm.getAdditional_risks());
			eoContent.setArrangement(contentForm.getArrangement());
			eoContent.setBoundary(contentForm.getBoundary());
			eoContent.setContact_contract_customer(contentForm
					.getContact_contract_customer());
			eoContent.setContact_tech_customer(contentForm
					.getContact_tech_customer());
			eoContent.setContactContractHbt(employeeDAO
					.getEmployeeById(contentForm
							.getContact_contract_hbt_emp_id()));
			eoContent.setContactTechHbt(employeeDAO.getEmployeeById(contentForm
					.getContact_tech_hbt_emp_id()));
			eoContent.setDescription(contentForm.getDescription());
			eoContent.setProcedure(contentForm.getProcedure());
			eoContent.setQm_process_id(contentForm.getQm_process_id());
			eoContent.setTask(contentForm.getTask());

			// after saving commits are no longer valid
			eoContent.setCommitted_emp(false); // why?!?!
			// <------------------------------------------
			eoContent.setCommitted_mgmt(false); // why?!?!
			// <------------------------------------------

			employeeOrderContentDAO.save(eoContent, loginEmployee);

			// set content in employeeorder and save
			if (employeeorder == null) {
				final Long eoId = (Long) request.getSession().getAttribute(
						"currentEmployeeOrderId");
				employeeorder = employeeorderDAO.getEmployeeorderById(eoId);
				if (employeeorder == null) {
					employeeOrderContentDAO
							.deleteEmployeeOrderContentById(eoContent.getId());
				} else {
					employeeorder.setEmployeeordercontent(eoContent);
					employeeorderDAO.save(employeeorder, loginEmployee);
				}
			}

			// status: saved
			request.getSession().setAttribute("contentStatus",
					"id " + eoContent.getId());

			// release authorization
			setReleaseAuthorizationInSession(request, employeeorder, eoContent);

			// set action info
			request.getSession().setAttribute(
					"actionInfo",
					getResources(request).getMessage(getLocale(request),
							"employeeordercontent.actioninfo.saved.text"));

			// set current employeeorder and content in session
			request.getSession().setAttribute("currentEmployeeOrder",
					employeeorder);
			request.getSession().setAttribute("eoContent", eoContent);

			return mapping.findForward("success");
		} // end action save

		// action release emp
		if ((request.getParameter("action") != null)
				&& (request.getParameter("action").equals("releaseEmp"))) {

			final Employeeorder employeeorder = (Employeeorder) request
					.getSession().getAttribute("currentEmployeeOrder");

			// check if, content is ready to be released (user is authorized AND
			// form entries equal dates in db)
			// user is authorized, if
			// status = admin
			// OR
			// login user = user associated with employeeorder
			if ((loginEmployee.equals(employeeorder.getEmployeecontract()
					.getEmployee()) || loginEmployee.getStatus().equals(
					GlobalConstants.EMPLOYEE_STATUS_ADM))
					&& employeeorder.getEmployeeordercontent() != null
					&& formEntriesEqualDB(employeeorder
							.getEmployeeordercontent().getId(), contentForm)) {
				// get content from db, if it was saved just before
				if (eoContent == null) {
					eoContent = employeeOrderContentDAO
							.getEmployeeOrderContentById(employeeorder
									.getEmployeeordercontent().getId());
				}
				eoContent.setCommitted_emp(true);
				eoContent.setCommittedby_emp(employeeDAO
						.getEmployeeById(loginEmployee.getId()));
				employeeOrderContentDAO.save(eoContent, loginEmployee);

				// set action info
				request
						.getSession()
						.setAttribute(
								"actionInfo",
								getResources(request)
										.getMessage(getLocale(request),
												"employeeordercontent.actioninfo.released.text"));

				// set updated employeeorder in session
				employeeorder.setEmployeeordercontent(eoContent);
				request.getSession().setAttribute("currentEmployeeOrder",
						employeeorder);

				// content is editable?
				request.getSession().setAttribute("contentIsEditable",
						isContentEditable(request, employeeorder, eoContent));

				// release authorization
				setReleaseAuthorizationInSession(request, employeeorder,
						eoContent);

			} else {
				request
						.getSession()
						.setAttribute(
								"actionInfo",
								getResources(request)
										.getMessage(getLocale(request),
												"employeeordercontent.actioninfo.notreleased.text"));
			}

			return mapping.findForward("success");
		} // action release emp end

		// action release mgmt
		if ((request.getParameter("action") != null)
				&& (request.getParameter("action").equals("releaseMgmt"))) {

			final Employeeorder employeeorder = (Employeeorder) request
					.getSession().getAttribute("currentEmployeeOrder");

			/*
			 * HACK: the contentForm "forgets" some values, if the input
			 * component is disabled. We have to set some values manually,
			 * if the fields are disabled:
			 */
			boolean editable = (Boolean) request.getSession().getAttribute(
					"contentIsEditable");
			if (!editable) {
				final Employeeordercontent eoc = employeeOrderContentDAO
						.getEmployeeOrderContentById(employeeorder
								.getEmployeeordercontent().getId());
				contentForm.setContact_contract_customer(eoc
						.getContact_contract_customer());
				contentForm.setContact_tech_customer(eoc
						.getContact_tech_customer());
				contentForm.setContact_contract_hbt_emp_id(eoc
						.getContactContractHbt().getId());
				contentForm.setContact_tech_hbt_emp_id(eoc.getContactTechHbt()
						.getId());
				contentForm.setQm_process_id(eoc.getQm_process_id());
			}

			if ((loginEmployee.equals(eoContent.getContactTechHbt())
					|| loginEmployee.getStatus().equals(
							GlobalConstants.EMPLOYEE_STATUS_ADM) || loginEmployee
					.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_PV))
					&& employeeorder.getEmployeeordercontent() != null
					&& formEntriesEqualDB(employeeorder
							.getEmployeeordercontent().getId(), contentForm)) {
				// get content from db, if it was saved just before
				if (eoContent == null) {
					eoContent = employeeOrderContentDAO
							.getEmployeeOrderContentById(employeeorder
									.getEmployeeordercontent().getId());
				}
				eoContent.setCommitted_mgmt(true);
				eoContent.setCommittedby_mgmt(employeeDAO
						.getEmployeeById(loginEmployee.getId()));
				employeeOrderContentDAO.save(eoContent, loginEmployee);

				// set action info
				request
						.getSession()
						.setAttribute(
								"actionInfo",
								getResources(request)
										.getMessage(getLocale(request),
												"employeeordercontent.actioninfo.released.text"));

				// set updated employeeorder in session
				employeeorder.setEmployeeordercontent(eoContent);
				request.getSession().setAttribute("currentEmployeeOrder",
						employeeorder);

				// content is editable?
				request.getSession().setAttribute("contentIsEditable",
						isContentEditable(request, employeeorder, eoContent));

				// release authorization
				setReleaseAuthorizationInSession(request, employeeorder,
						eoContent);

			} else {
				request
						.getSession()
						.setAttribute(
								"actionInfo",
								getResources(request)
										.getMessage(getLocale(request),
												"employeeordercontent.actioninfo.notreleased.text"));
			}

			return mapping.findForward("success");
		} // action release mgmt end

		// action delete (for admin only!!!)
		if ((request.getParameter("action") != null)
				&& (request.getParameter("action").equals("deleteContent"))) {

			if (loginEmployee.getStatus().equals(
					GlobalConstants.EMPLOYEE_STATUS_ADM)
					&& eoContent != null
					&& request.getSession()
							.getAttribute("currentEmployeeOrder") != null) {
				Employeeorder employeeorder = (Employeeorder) request
						.getSession().getAttribute("currentEmployeeOrder");
				// get employeeorder fresh from db
				employeeorder = employeeorderDAO
						.getEmployeeorderById(employeeorder.getId());
				// set content to null and save
				employeeorder.setEmployeeordercontent(null);
				employeeorderDAO.save(employeeorder, loginEmployee);
				// delete content
				employeeOrderContentDAO
						.deleteEmployeeOrderContentById(eoContent.getId());
			}

			// go on with back action
			backAction = true;
		} // action delete end

		// action removeRelease (for admin only!!!)
		if ((request.getParameter("action") != null)
				&& (request.getParameter("action").equals("removeRelease"))) {

			if (loginEmployee.getStatus().equals(
					GlobalConstants.EMPLOYEE_STATUS_ADM)
					&& eoContent != null) {
				eoContent.setCommitted_emp(false);
				eoContent.setCommitted_mgmt(false);
				employeeOrderContentDAO.save(eoContent, loginEmployee);

				// set action info
				request
						.getSession()
						.setAttribute(
								"actionInfo",
								getResources(request)
										.getMessage(getLocale(request),
												"employeeordercontent.actioninfo.removedrelease.text"));

				final Employeeorder employeeorder = employeeorderDAO
						.getEmployeeOrderByContentId(eoContent.getId());

				// set current employeeorder in session
				request.getSession().setAttribute("currentEmployeeOrder",
						employeeorder);

				// content is editable?
				request.getSession().setAttribute("contentIsEditable",
						isContentEditable(request, employeeorder, eoContent));

				// release authorization
				setReleaseAuthorizationInSession(request, employeeorder,
						eoContent);

				return mapping.findForward("success");
			}

			// set action info
			request.getSession().setAttribute(
					"actionInfo",
					getResources(request).getMessage(getLocale(request),
							"employeeordercontent.actioninfo.error.text"));

			return mapping.findForward("success");
		} // action delete end

		// action back
		if (((request.getParameter("action") != null) && (request
				.getParameter("action").equals("back")))
				|| backAction) {

			// get filter settings from session and refresh list of
			// employeeorders for overview
			final Employeecontract employeecontract = (Employeecontract) request
					.getSession().getAttribute("currentEmployeeContract");
			long employeeContractId = -1;
			if (employeecontract != null) {
				employeeContractId = employeecontract.getId();
			}

			String filter = null;
			Boolean show = null;

			final long orderId = (Long) request.getSession().getAttribute(
					"currentOrderId");
			if (request.getSession().getAttribute("employeeOrderFilter") != null) {
				filter = (String) request.getSession().getAttribute(
						"employeeOrderFilter");
			}
			if (request.getSession().getAttribute("employeeOrderShow") != null) {
				show = (Boolean) request.getSession().getAttribute(
						"employeeOrderShow");
			}
			request.getSession().setAttribute(
					"employeeorders",
					employeeorderDAO.getEmployeeordersByFilters(show, filter,
							employeeContractId, orderId));

			return mapping.findForward("back");
		} // action back end

		// no action selected - show page again
		return mapping.findForward("success");
	}

	/**
	 * Checks, if dates from form equal dates from database.
	 * 
	 * @param eoContent
	 * @param contentForm
	 * @return Returns true, if dates are equal, false otherwise. If an
	 *         {@link NullPointerException} occurs, false is returned.
	 */
	private boolean formEntriesEqualDB(final Long eocId,
			final AddEmployeeOrderContentForm contentForm) {
		try {
			final Employeeordercontent eoContent = employeeOrderContentDAO
					.getEmployeeOrderContentById(eocId);

			if (!eoContent.getAdditional_risks().equals(
					contentForm.getAdditional_risks())) {
				return false;
			}
			if (!eoContent.getArrangement()
					.equals(contentForm.getArrangement())) {
				return false;
			}
			if (!eoContent.getBoundary().equals(contentForm.getBoundary())) {
				return false;
			}
			if (!eoContent.getContact_contract_customer().equals(
					contentForm.getContact_contract_customer())) {
				return false;
			}
			if (!eoContent.getContact_tech_customer().equals(
					contentForm.getContact_tech_customer())) {
				return false;
			}
			if (eoContent.getContactContractHbt().getId() != contentForm
					.getContact_contract_hbt_emp_id()) {
				return false;
			}
			if (eoContent.getContactTechHbt().getId() != contentForm
					.getContact_tech_hbt_emp_id()) {
				return false;
			}
			if (!eoContent.getDescription()
					.equals(contentForm.getDescription())) {
				return false;
			}
			if (!eoContent.getProcedure().equals(contentForm.getProcedure())) {
				return false;
			}
			if (!eoContent.getQm_process_id().equals(
					contentForm.getQm_process_id())) {
				return false;
			}
			if (!eoContent.getTask().equals(contentForm.getTask())) {
				return false;
			}

			return true;
		} catch (final NullPointerException e) {
			return false;
		}
	}

	public void setEmployeeDAO(final EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}

	public void setEmployeeOrderContentDAO(
			final EmployeeOrderContentDAO employeeOrderContentDAO) {
		this.employeeOrderContentDAO = employeeOrderContentDAO;
	}

	public void setEmployeeorderDAO(final EmployeeorderDAO employeeorderDAO) {
		this.employeeorderDAO = employeeorderDAO;
	}

	/**
	 * Validates the form data.
	 * 
	 * @param request
	 * @param contentForm
	 * @return Returns the errors as {@link ActionMessages}.
	 */
	private ActionMessages validateFormData(final HttpServletRequest request,
			final AddEmployeeOrderContentForm contentForm) {

		ActionMessages errors = getErrors(request);
		if (errors == null) {
			errors = new ActionMessages();
		}

		// check description
		final String description = contentForm.getDescription();
		if (description.length() > GlobalConstants.FORM_MAX_CHAR_BIG_TEXTAREA) {
			errors.add("description", new ActionMessage(
					"form.error.toomanychars.2048.text"));
		}

		// check boundary
		final String boundary = contentForm.getBoundary();
		if (boundary.length() > GlobalConstants.FORM_MAX_CHAR_BIG_TEXTAREA) {
			errors.add("boundary", new ActionMessage(
					"form.error.toomanychars.2048.text"));
		}

		// check procedure
		final String procedure = contentForm.getProcedure();
		if (procedure.length() > GlobalConstants.FORM_MAX_CHAR_BIG_TEXTAREA) {
			errors.add("procedure", new ActionMessage(
					"form.error.toomanychars.2048.text"));
		}

		// check task
		final String task = contentForm.getTask();
		if (task.length() > GlobalConstants.FORM_MAX_CHAR_BIG_TEXTAREA) {
			errors.add("task", new ActionMessage(
					"form.error.toomanychars.2048.text"));
		}

		// check contact_contract_customer
		final String contact_contract_customer = contentForm
				.getContact_contract_customer();
		if (contact_contract_customer.length() > GlobalConstants.FORM_MAX_CHAR_NAME_TEXTFIELD) {
			errors.add("contact_contract_customer", new ActionMessage(
					"form.error.toomanychars.64.text"));
		}

		// check contact_tech_customer
		final String contact_tech_customer = contentForm
				.getContact_tech_customer();
		if (contact_tech_customer.length() > GlobalConstants.FORM_MAX_CHAR_NAME_TEXTFIELD) {
			errors.add("contact_tech_customer", new ActionMessage(
					"form.error.toomanychars.64.text"));
		}

		// check additional risks
		final String risks = contentForm.getAdditional_risks();
		if (risks.length() > GlobalConstants.FORM_MAX_CHAR_BIG_TEXTAREA) {
			errors.add("risks", new ActionMessage(
					"form.error.toomanychars.2048.text"));
		}

		// check arrangement
		final String arrangement = contentForm.getArrangement();
		if (arrangement.length() > GlobalConstants.FORM_MAX_CHAR_BIG_TEXTAREA) {
			errors.add("arrangement", new ActionMessage(
					"form.error.toomanychars.2048.text"));
		}

		saveErrors(request, errors);

		return errors;
	}

}
