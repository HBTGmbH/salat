package org.tb.web.action.admin;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Statusreport;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.StatusReportDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.ShowStatusReportForm;

public class ShowStatusReportAction extends LoginRequiredAction {

	private StatusReportDAO statusReportDAO;
	private CustomerorderDAO customerorderDAO;
	
	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}
	public void setStatusReportDAO(StatusReportDAO statusReportDAO) {
		this.statusReportDAO = statusReportDAO;
	}
	
	@Override
	protected ActionForward executeAuthenticated(ActionMapping mapping,
			ActionForm form, HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		
		ShowStatusReportForm reportForm = (ShowStatusReportForm) form;
		
		request.getSession().setAttribute("visibleCustomerOrders", customerorderDAO.getVisibleCustomerorders());
		
		long customerOrderId;
		if (reportForm != null && reportForm.getCustomerOrderId() != null) {
			customerOrderId = reportForm.getCustomerOrderId();
		} else {
			customerOrderId = -1l;
		}
		
		if (request.getParameter("coId") != null) {
			customerOrderId = Long.parseLong(request.getParameter("coId"));
		}
		
		
		List<Statusreport> statusReports;
		
		if (customerOrderId != 0 && customerOrderId != -1) {
			statusReports = statusReportDAO.getStatusReportsByCustomerOrderId(customerOrderId);
		} else {
			statusReports = statusReportDAO.getVisibleStatusReports();
		}
		request.getSession().setAttribute("statusReports", statusReports);
		request.getSession().setAttribute("customerOrderId", customerOrderId);
		
		return mapping.findForward("success");
	}

}
