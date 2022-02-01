package org.tb.web.action.admin;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Statusreport;
import org.tb.persistence.StatusReportDAO;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

public class DeleteStatusReportAction extends StatusReportAction<ActionForm> {

    private StatusReportDAO statusReportDAO;

    public void setStatusReportDAO(StatusReportDAO statusReportDAO) {
        this.statusReportDAO = statusReportDAO;
    }

    /* (non-Javadoc)
     * @see org.tb.web.action.LoginRequiredAction#executeAuthenticated(org.apache.struts.action.ActionMapping, org.apache.struts.action.ActionForm, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {

        // get status report to be deleted
        Statusreport statusreport = null;

        try {
            long srId = Long.parseLong(request.getParameter("srId"));
            statusreport = statusReportDAO.getStatusReportById(srId);
        } catch (Exception e) {
            // do nothing here
        }
        // go to error page, if statusreport is null
        if (statusreport == null) {
            request.setAttribute("errorMessage",
                    "Status report not found - please call system administrator.");
            return mapping.findForward("error");
        }

        statusReportDAO.deleteStatusReportById(statusreport.getId());

        // refresh list of reports for overview
        Long customerOrderId = (Long) request.getSession().getAttribute("customerOrderId");

        List<Statusreport> statusReports;
        if (customerOrderId != 0 && customerOrderId != -1) {
            statusReports = statusReportDAO.getStatusReportsByCustomerOrderId(customerOrderId);
        } else {
            statusReports = statusReportDAO.getVisibleStatusReports();
        }

        request.getSession().setAttribute("statusReports", statusReports);


        return mapping.findForward("success");
    }


}
