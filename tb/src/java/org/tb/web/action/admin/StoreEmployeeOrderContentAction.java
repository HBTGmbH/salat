package org.tb.web.action.admin;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
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
	
	public void setEmployeeOrderContentDAO(EmployeeOrderContentDAO employeeOrderContentDAO) {
		this.employeeOrderContentDAO = employeeOrderContentDAO;
	}
	
	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}
	
	public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
		this.employeeorderDAO = employeeorderDAO;
	}
	
	
	@Override
	protected ActionForward executeAuthenticated(ActionMapping mapping,
			ActionForm form, HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		
		// boolean to trigger back action
		boolean backAction = false;
		
		// remove status
//		request.getSession().removeAttribute("contentStatus");
		
		
		AddEmployeeOrderContentForm contentForm = (AddEmployeeOrderContentForm) form;
		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
		
		Employeeordercontent eoContent = null;
		
		// existing or new eoContent
		if (request.getSession().getAttribute("eoContent") != null) {
			eoContent = (Employeeordercontent) request
					.getSession().getAttribute("eoContent");
		}
		
		// get the same eoContent fresh from db
		if (eoContent != null) {
			eoContent = employeeOrderContentDAO.getEmployeeOrderContentById(eoContent.getId());
		}
		
		
		// action save
		if ((request.getParameter("action") != null)
				&& (request.getParameter("action").equals("save"))) {
			
			Employeeorder employeeorder = null;
			
  			// validate
			// TODO
			
			// test, if login user is authrized to safe
			boolean authorized = false;
			if (eoContent == null) {
				eoContent = new Employeeordercontent();
				authorized = true;
			} else {
				employeeorder = employeeorderDAO.getEmployeeOrderByContentId(eoContent.getId());
				if (employeeorder == null) {
					return mapping.findForward("error");
				}
				Employee empFromEO = employeeorder.getEmployeecontract().getEmployee();
				if (empFromEO != null && 
						empFromEO.equals(loginEmployee) &&
						(!eoContent.getCommitted_emp() ||
						 !eoContent.getCommitted_mgmt())) {
					authorized = true;
				} else if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_GF) &&
						   (!eoContent.getCommitted_emp() ||
									 !eoContent.getCommitted_mgmt())) {
					authorized = true;
				} else if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
					authorized = true;
				}				
			}
			if (!authorized) {
				request.getSession().setAttribute("actionInfo", 
						getResources(request).getMessage(getLocale(request), "employeeordercontent.actioninfo.notauthorized.text"));
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
			eoContent.setCommitted_emp(false);
			eoContent.setCommitted_mgmt(false);
			
			employeeOrderContentDAO.save(eoContent, loginEmployee);
			
			// set content in employeeorder and save
			if (employeeorder == null) {				
				Long eoId = (Long) request.getSession().getAttribute("currentEmployeeOrderId");
				employeeorder = employeeorderDAO.getEmployeeorderById(eoId);
				if (employeeorder == null) {
					employeeOrderContentDAO.deleteEmployeeOrderContentById(eoContent.getId());
				} else {
					employeeorder.setEmployeeordercontent(eoContent);
					employeeorderDAO.save(employeeorder, loginEmployee);
				}
			}		
			
			// status: saved
			request.getSession().setAttribute("contentStatus", "id "+eoContent.getId());
						
			// release authorization
			setReleaseAuthorizationInSession(request, employeeorder, eoContent);
			
			// set action info
			request.getSession().setAttribute("actionInfo", 
					getResources(request).getMessage(getLocale(request), "employeeordercontent.actioninfo.saved.text"));
			
			// set current employeeorder and content in session
			request.getSession().setAttribute("currentEmployeeOrder", employeeorder);
			request.getSession().setAttribute("eoContent", eoContent);
			
			return mapping.findForward("success");
		} // end action save
		
		
		// action release emp
		if ((request.getParameter("action") != null)
				&& (request.getParameter("action").equals("releaseEmp"))) {
			
			Employeeorder employeeorder = (Employeeorder) request.getSession().getAttribute("currentEmployeeOrder");
			
			// check if, content is ready to be released (user is authorized AND form entries equal dates in db)
			// user is authorized, if
			//  status = admin
			// OR
			//  login user = user associated with employeeorder
			if ((loginEmployee.equals(employeeorder.getEmployeecontract().getEmployee()) ||
				 loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM)) &&
				 		employeeorder.getEmployeeordercontent() != null &&
						formEntriesEqualDB(employeeorder.getEmployeeordercontent().getId(), contentForm)) {
				// get content from db, if it was saved just before
				if (eoContent == null) {
					eoContent = employeeOrderContentDAO.getEmployeeOrderContentById(employeeorder.getEmployeeordercontent().getId());
				}
				eoContent.setCommitted_emp(true);
				eoContent.setCommittedby_emp(employeeDAO.getEmployeeById(loginEmployee.getId()));
				employeeOrderContentDAO.save(eoContent, loginEmployee);
				
				// set action info
				request.getSession().setAttribute("actionInfo", 
						getResources(request).getMessage(getLocale(request), "employeeordercontent.actioninfo.released.text"));
				
				// set updated employeeorder in session
				employeeorder.setEmployeeordercontent(eoContent);
				request.getSession().setAttribute("currentEmployeeOrder", employeeorder);
				
				// content is editable?
				request.getSession().setAttribute("contentIsEditable", isContentEditable(request, employeeorder, eoContent));
				
				// release authorization
				setReleaseAuthorizationInSession(request, employeeorder, eoContent);
				
			} else {
				request.getSession().setAttribute("actionInfo", 
						getResources(request).getMessage(getLocale(request), "employeeordercontent.actioninfo.notreleased.text"));
			}
			
			
			return mapping.findForward("success");
		} // action release emp end
		
		// action release mgmt
		if ((request.getParameter("action") != null)
				&& (request.getParameter("action").equals("releaseMgmt"))) {
			
			Employeeorder employeeorder = (Employeeorder) request.getSession().getAttribute("currentEmployeeOrder");
			
			// check if, content is ready to be released (user is authorized AND form entries equal dates in db)
			// user is authorized, if
			//  status = admin
			// OR
			//  status = gf
			if ((loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_GF) ||
				 loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM)) &&
				 		employeeorder.getEmployeeordercontent() != null &&
						formEntriesEqualDB(employeeorder.getEmployeeordercontent().getId(), contentForm)) {
				// get content from db, if it was saved just before
				if (eoContent == null) {
					eoContent = employeeOrderContentDAO.getEmployeeOrderContentById(employeeorder.getEmployeeordercontent().getId());
				}
				eoContent.setCommitted_mgmt(true);
				eoContent.setCommittedby_mgmt(employeeDAO.getEmployeeById(loginEmployee.getId()));
				employeeOrderContentDAO.save(eoContent, loginEmployee);
				
				// set action info
				request.getSession().setAttribute("actionInfo", 
						getResources(request).getMessage(getLocale(request), "employeeordercontent.actioninfo.released.text"));
				
				// set updated employeeorder in session
				employeeorder.setEmployeeordercontent(eoContent);
				request.getSession().setAttribute("currentEmployeeOrder", employeeorder);
				
				// content is editable?
				request.getSession().setAttribute("contentIsEditable", isContentEditable(request, employeeorder, eoContent));
				
				// release authorization
				setReleaseAuthorizationInSession(request, employeeorder, eoContent);
				
			} else {
				request.getSession().setAttribute("actionInfo", 
						getResources(request).getMessage(getLocale(request), "employeeordercontent.actioninfo.notreleased.text"));
			}		
			
			
			return mapping.findForward("success");
		} // action release mgmt end
		
		
		
		// action delete (for admin only!!!)
		if ((request.getParameter("action") != null)
				&& (request.getParameter("action").equals("deleteContent"))) {
			
			if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM) 
					&& eoContent != null 
					&& request.getSession().getAttribute("currentEmployeeOrder") != null) {
				Employeeorder employeeorder = (Employeeorder) request.getSession().getAttribute("currentEmployeeOrder");
				// get employeeorder fresh from db
				employeeorder = employeeorderDAO.getEmployeeorderById(employeeorder.getId());
				// set content to null and save
				employeeorder.setEmployeeordercontent(null);
				employeeorderDAO.save(employeeorder, loginEmployee);
				// delete content
				employeeOrderContentDAO.deleteEmployeeOrderContentById(eoContent.getId());
			}
			
			// go on with back action
			backAction = true;
		} // action delete end
		
		
		// action removeRelease (for admin only!!!)
		if ((request.getParameter("action") != null)
				&& (request.getParameter("action").equals("removeRelease"))) {
			
			if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM) 
					&& eoContent != null) {
				eoContent.setCommitted_emp(false);
				eoContent.setCommitted_mgmt(false);
				employeeOrderContentDAO.save(eoContent, loginEmployee);
				
				// set action info
				request.getSession().setAttribute("actionInfo", 
						getResources(request).getMessage(getLocale(request), "employeeordercontent.actioninfo.removedrelease.text"));
				
				Employeeorder employeeorder = employeeorderDAO.getEmployeeOrderByContentId(eoContent.getId());
				
				// set current employeeorder in session
				request.getSession().setAttribute("currentEmployeeOrder", employeeorder);
				
				// content is editable?
				request.getSession().setAttribute("contentIsEditable", isContentEditable(request, employeeorder, eoContent));
				
				// release authorization
				setReleaseAuthorizationInSession(request, employeeorder, eoContent);
				
				return mapping.findForward("success");
			}
			
			// set action info
			request.getSession().setAttribute("actionInfo", 
					getResources(request).getMessage(getLocale(request), "employeeordercontent.actioninfo.error.text"));
			
			return mapping.findForward("success");
		} // action delete end
		
		
		// action back
		if (((request.getParameter("action") != null)
				&& (request.getParameter("action").equals("back"))) || backAction) {
			
			
			// get filter settings from session and refresh list of employeeorders for overview 
			Employeecontract employeecontract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
			long employeeContractId = -1;
			if (employeecontract != null) {
				employeeContractId = employeecontract.getId();
			}
			
			long orderId = (Long) request.getSession().getAttribute("currentOrderId");
			String filter = null;
			Boolean show = null;
	
			if (request.getSession().getAttribute("employeeOrderFilter") != null) {
				filter = (String) request.getSession().getAttribute("employeeOrderFilter");
			}
			if (request.getSession().getAttribute("employeeOrderShow") != null) {
				show = (Boolean) request.getSession().getAttribute("employeeOrderShow");
			}

			request.getSession().setAttribute("employeeorders", employeeorderDAO.getEmployeeordersByFilters(show, filter, employeeContractId, orderId));

			
			return mapping.findForward("back");
		} // action back end
		
		
		// error
		return mapping.findForward("error");
	}
	
	
	/**
	 * Checks, if dates from form equal dates from database.
	 * 
	 * @param eoContent
	 * @param contentForm
	 * @return Returns true, if dates are equal, false otherwise. If an {@link NullPointerException} occurs, false is returned.
	 */
	private boolean formEntriesEqualDB(Long eocId, AddEmployeeOrderContentForm contentForm) {			
		try {
			Employeeordercontent eoContent = employeeOrderContentDAO.getEmployeeOrderContentById(eocId);
			return (eoContent.getAdditional_risks().equals(
					contentForm.getAdditional_risks())
					&& eoContent.getArrangement().equals(
							contentForm.getArrangement())
					&& eoContent.getBoundary()
							.equals(contentForm.getBoundary())
					&& eoContent.getContact_contract_customer().equals(
							contentForm.getContact_contract_customer())
					&& eoContent.getContact_tech_customer().equals(
							contentForm.getContact_tech_customer())
					&& eoContent.getContactContractHbt().getId() == contentForm
							.getContact_contract_hbt_emp_id()
					&& eoContent.getContactTechHbt().getId() == contentForm
							.getContact_tech_hbt_emp_id()
					&& eoContent.getDescription().equals(
							contentForm.getDescription())
					&& eoContent.getProcedure().equals(
							contentForm.getProcedure())
					&& eoContent.getQm_process_id().equals(contentForm
							.getQm_process_id())
					&& eoContent.getTask().equals(contentForm.getTask()));
		} catch (NullPointerException e) {
			return false;
		}		
	}

}
