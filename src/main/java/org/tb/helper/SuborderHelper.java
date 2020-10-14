package org.tb.helper;

import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Suborder;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TicketDAO;
import org.tb.web.form.AddDailyReportForm;
import org.tb.web.form.ShowDailyReportForm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Helper class for suborder handling which does not directly deal with persistence
 *
 * @author oda
 */
public class SuborderHelper {

    /**
     * refreshes suborder list after change of customer order in the 'add timereport' view
     *
     * @param mapping
     * @param request
     * @param reportForm              - AddDailyReportForm
     * @param sd                      - SuborderDAO being used
     * @param ecd                     - EmployeecontractDAO being used
     * @param defaultSuborderIndexStr
     * @return boolean
     */
    public boolean refreshSuborders(ActionMapping mapping, HttpServletRequest request, AddDailyReportForm reportForm,
                                    SuborderDAO sd, TicketDAO td, EmployeecontractDAO ecd, String defaultSuborderIndexStr) {

        Employeecontract ec = ecd.getEmployeeContractById(reportForm.getEmployeeContractId());

        if (ec == null) {
            request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator.");
            return false;
        }

        String dateString = reportForm.getReferenceday();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
        Date date;
        try {
            date = simpleDateFormat.parse(dateString);
        } catch (Exception e) {
            throw new RuntimeException("error while parsing date");
        }

        // get suborders related to employee AND selected customer order
        long customerorderId = reportForm.getOrderId();
        List<Suborder> theSuborders = sd.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(ec.getId(), customerorderId, date);
        request.getSession().setAttribute("suborders", theSuborders);
        Suborder so = null;
        if (defaultSuborderIndexStr != null) {
            try {
                Long currentSuborderId = Long.parseLong(defaultSuborderIndexStr);
                request.getSession().setAttribute("currentSuborderId", currentSuborderId);
                so = sd.getSuborderById(currentSuborderId);
            } catch (NumberFormatException e) {
            } // do nothing
        }

        // set the first Suborder as current
        so = so != null ? so : theSuborders.get(0);
        if (so != null) {
            assignCurrentSuborderIdWithOvertimeCompensationAndTrainingFlag(request.getSession(), so, reportForm);
            JiraSalatHelper.setJiraTicketKeysForSuborder(request, td, so.getId());
        }

        return true;
    }

    /**
     * refreshes suborder list after change of customer order in the 'show timereport' views
     *
     * @param mapping
     * @param request
     * @param reportForm - ShowDailyReportForm
     * @param sd         - SuborderDAO being used
     * @param ecd        - EmployeecontractDAO being used
     * @return boolean
     */
    public boolean refreshDailyOverviewSuborders(ActionMapping mapping, HttpServletRequest request, ShowDailyReportForm reportForm,
                                                 SuborderDAO sd, EmployeecontractDAO ecd) {

        Employeecontract ec = ecd.getEmployeeContractById(reportForm.getEmployeeContractId());

        if (ec == null) {
            request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator.");
            return false;
        }

        // get suborders related to employee AND selected customer order...
        long customerorderId = reportForm.getTrOrderId();
        request.getSession().setAttribute("suborders", sd.getSubordersByEmployeeContractIdAndCustomerorderId(ec.getId(), customerorderId, reportForm.getShowOnlyValid()));

        return true;
    }

    public void adjustSuborderSignChanged(HttpSession session, AddDailyReportForm reportForm, SuborderDAO sd) {
        Suborder so = sd.getSuborderById(reportForm.getSuborderSignId());
        assignCurrentSuborderIdWithOvertimeCompensationAndTrainingFlag(session, so, reportForm);
    }

    public void adjustSuborderDescriptionChanged(HttpSession session, AddDailyReportForm reportForm, SuborderDAO sd) {
        Suborder so = sd.getSuborderById(reportForm.getSuborderDescriptionId());
        assignCurrentSuborderIdWithOvertimeCompensationAndTrainingFlag(session, so, reportForm);
    }

    private void assignCurrentSuborderIdWithOvertimeCompensationAndTrainingFlag(HttpSession session, Suborder so, AddDailyReportForm reportForm) {
        session.setAttribute("currentSuborderId", so.getId());

        // if selected Suborder is Overtime Compensation, delete the previously automatically set daily working time
        // also make sure that overtimeCompensation is set in the session so that the duration-dropdown-menu will be disabled
        if (so.getSign().equalsIgnoreCase(GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
            reportForm.setSelectedHourDuration(0);
            reportForm.setSelectedMinuteDuration(0);
            if (session.getAttribute("overtimeCompensation") == null ||
                    session.getAttribute("overtimeCompensation") != GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION) {
                session.setAttribute("overtimeCompensation", GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION);
            }
        } else {
            session.removeAttribute("overtimeCompensation");
        }

        // if selected Suborder has a default-flag for projectbased training, set training in the form to true, so that the training-box in the jsp is checked
        if (so.getTrainingFlag()) {
            reportForm.setTraining(true);
        }
    }
}
