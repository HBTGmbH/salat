package org.tb.action.admin;

import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.action.LoginRequiredAction;
import org.tb.bdom.Statusreport;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.StatusReportDAO;
import org.tb.form.ShowStatusReportForm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@Component
public class ShowStatusReportAction extends LoginRequiredAction<ShowStatusReportForm> {

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
        ShowStatusReportForm reportForm, HttpServletRequest request,
        HttpServletResponse response) throws Exception {

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
        if (reportForm != null) {
            request.getSession().setAttribute("showReleased", reportForm.getShowReleased());
        }

        return mapping.findForward("success");
    }

}
