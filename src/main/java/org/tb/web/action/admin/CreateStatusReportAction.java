package org.tb.web.action.admin;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Statusreport;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.StatusReportDAO;
import org.tb.web.form.AddStatusReportForm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

public class CreateStatusReportAction extends StatusReportAction<AddStatusReportForm> {

    private EmployeeDAO employeeDAO;
    private CustomerorderDAO customerorderDAO;
    private StatusReportDAO statusReportDAO;

    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }

    public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
        this.customerorderDAO = customerorderDAO;
    }

    public void setStatusReportDAO(StatusReportDAO statusReportDAO) {
        this.statusReportDAO = statusReportDAO;
    }

    /* (non-Javadoc)
     * @see org.tb.web.action.LoginRequiredAction#executeAuthenticated(org.apache.struts.action.ActionMapping, org.apache.struts.action.ActionForm, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping, AddStatusReportForm reportForm, HttpServletRequest request, HttpServletResponse response) throws Exception {

        // remove action info
        request.getSession().removeAttribute("actionInfo");

        // set report status
        request.getSession().setAttribute("reportStatus", getResources(request).getMessage(getLocale(request), "statusreport.reportstatus.new.text"));

        // set null as current statusreport
        request.getSession().setAttribute("currentStatusReport", null);

        // set collection of phases
        request.getSession().setAttribute("phases", getPhaseOptionList(request));

        // set collection of report sorts
        request.getSession().setAttribute("sorts", getSortOptionList(request));

        // set collection of employees for jsp
        List<Employee> employees = employeeDAO.getEmployeesWithContracts();
        request.getSession().setAttribute("employees", employees);

        // set collection of customers
        request.getSession().setAttribute("visibleCustomerOrders", customerorderDAO.getVisibleCustomerorders());

        // report editable
        request.getSession().setAttribute("isReportEditable", isReportEditable(null, request));

        // is report ready for release
        request.getSession().setAttribute("isReportReadyForRelease", isReportReadyForRelease(null, statusReportDAO, request));

        // is report ready for acceptance
        request.getSession().setAttribute("isReportReadyForAcceptance", isReportReadyForAcceptance(null, statusReportDAO, request));

        // presettings for the selected customer order
        Long customerOrderId = (Long) request.getSession().getAttribute("customerOrderId");
        @SuppressWarnings("unchecked")
        List<Customerorder> customerOrders = (List<Customerorder>) request.getSession().getAttribute("visibleCustomerOrders");
        if (customerOrders == null || customerOrders.isEmpty()) {
            request.setAttribute("errorMessage",
                    "No customer orders found - please call system administrator.");
            return mapping.findForward("error");
        }
        Customerorder selectedCustomerOrder = null;

        // create status report from warning
        if (request.getParameter("coId") != null) {
            customerOrderId = Long.parseLong(request.getParameter("coId"));

            // debug
            @SuppressWarnings("unused")
            String x = request.getParameter("final");

            if (request.getParameter("final") != null
                    && request.getParameter("final").equals("true")) {
                reportForm.setSort((byte) 3);
            }
        }

        if (customerOrderId != null && customerOrderId != 0 && customerOrderId != -1) {
            selectedCustomerOrder = customerorderDAO.getCustomerorderById(customerOrderId);
        }

        if (selectedCustomerOrder == null) {
            selectedCustomerOrder = customerOrders.get(0);
        }
        request.getSession().setAttribute("selectedCustomerOrder", selectedCustomerOrder);

        List<Statusreport> statusReports = statusReportDAO.getStatusReportsByCustomerOrderId(selectedCustomerOrder.getId());

        Date fromDate = selectedCustomerOrder.getFromDate();
        if (statusReports != null && !statusReports.isEmpty()) {
            Statusreport lastKnownReport = statusReports.get(statusReports.size() - 1);
            fromDate = lastKnownReport.getUntildate();
            GregorianCalendar calendar = new GregorianCalendar();
            calendar.setTime(fromDate);
            calendar.add(Calendar.DATE, 1);
            fromDate.setTime(calendar.getTimeInMillis());
        }
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);

        byte one = 1;

        // set form entries
        reportForm.setCustomerOrderId(selectedCustomerOrder.getId());
        if (selectedCustomerOrder.getRespEmpHbtContract() != null) {
            reportForm.setRecipientId(selectedCustomerOrder
                    .getRespEmpHbtContract().getId());
        }
        if (selectedCustomerOrder.getResponsible_hbt() != null) {
            reportForm.setSenderId(selectedCustomerOrder.getResponsible_hbt()
                    .getId());
        }

        reportForm.setValidFrom(simpleDateFormat.format(fromDate));
        reportForm.setValidUntil(simpleDateFormat.format(new java.util.Date()));
        //new report -> status green (1)
        reportForm.setAim_status(one);
        reportForm.setBudget_resources_date_status(one);
        reportForm.setChangedirective_status(one);
        reportForm.setCommunication_status(one);
        reportForm.setImprovement_status(one);
        reportForm.setCustomerfeedback_status(one);
        reportForm.setMiscellaneous_status(one);
        reportForm.setNeedforaction_status(one);
        reportForm.setOverallStatus((byte) 0);
        reportForm.setRiskmonitoring_status(one);
        reportForm.setTrendstatus(one);

        // clear textfields
        reportForm.setAim_action("");
        reportForm.setAim_source("");
        reportForm.setAim_text("");
        reportForm.setAllocator("");
        reportForm.setBudget_resources_date_action("");
        reportForm.setBudget_resources_date_source("");
        reportForm.setBudget_resources_date_text("");
        reportForm.setChangedirective_action("");
        reportForm.setChangedirective_source("");
        reportForm.setChangedirective_text("");
        reportForm.setCommunication_action("");
        reportForm.setCommunication_source("");
        reportForm.setCommunication_text("");
        reportForm.setImprovement_action("");
        reportForm.setImprovement_source("");
        reportForm.setImprovement_text("");
        reportForm.setCustomerfeedback_source("");
        reportForm.setCustomerfeedback_text("");
        reportForm.setMiscellaneous_action("");
        reportForm.setMiscellaneous_source("");
        reportForm.setMiscellaneous_text("");
        reportForm.setNeedforaction_source("");
        reportForm.setNeedforaction_text("");
        reportForm.setNotes("");
        reportForm.setRiskmonitoring_action("");
        reportForm.setRiskmonitoring_source("");
        reportForm.setRiskmonitoring_text("");
        reportForm.setTrend((byte) 0);

        return mapping.findForward("success");
    }

}
