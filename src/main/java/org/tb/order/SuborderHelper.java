package org.tb.order;

import static org.tb.common.util.DateUtils.parse;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.common.GlobalConstants;
import org.tb.dailyreport.AddDailyReportForm;
import org.tb.dailyreport.ShowDailyReportForm;
import org.tb.employee.Employeecontract;
import org.tb.employee.EmployeecontractDAO;

/**
 * Helper class for suborder handling which does not directly deal with persistence
 *
 * @author oda
 */
@Component
@Slf4j
@RequiredArgsConstructor(onConstructor_ = { @Autowired})
public class SuborderHelper {

    private final SuborderDAO suborderDAO;
    private final EmployeecontractDAO employeecontractDAO;

    /**
     * refreshes suborder list after change of customer order in the 'add timereport' view
     */
    public boolean refreshSuborders(HttpServletRequest request, AddDailyReportForm reportForm, String defaultSuborderIndexStr) {

        // initial with empty values in case of an error
        request.getSession().removeAttribute("overtimeCompensation");
        request.getSession().setAttribute("currentSuborderId", -1);

        Employeecontract ec = employeecontractDAO.getEmployeeContractById(reportForm.getEmployeeContractId());

        if (ec == null) {
            // request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator.");
            return false;
        }

        String dateString = reportForm.getReferenceday();
        LocalDate date;
        try {
            date = parse(dateString);
        } catch (ParseException e) {
            throw new RuntimeException("error while parsing date");
        }

        // get suborders related to employee AND selected customer order
        long customerorderId = reportForm.getOrderId();
        List<Suborder> theSuborders = suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(ec.getId(), customerorderId, date);
        Suborder so = null;
        if (defaultSuborderIndexStr != null) {
            try {
                long currentSuborderId = Long.parseLong(defaultSuborderIndexStr);
                so = suborderDAO.getSuborderById(currentSuborderId);
            } catch (NumberFormatException ignore) {
            }
        }

        if (so != null) {
            assignCurrentSuborderIdWithOvertimeCompensationAndTrainingFlag(request.getSession(), so, reportForm);
        } else if (!theSuborders.isEmpty()) {
            // set the first Suborder as current
            so = theSuborders.get(0);
            assignCurrentSuborderIdWithOvertimeCompensationAndTrainingFlag(request.getSession(), so, reportForm);
        }

        request.getSession().setAttribute("suborders", theSuborders);
        return true;
    }

    /**
     * refreshes suborder list after change of customer order in the 'show timereport' views
     */
    public boolean refreshDailyOverviewSuborders(HttpServletRequest request, ShowDailyReportForm reportForm) {

        Employeecontract ec = employeecontractDAO.getEmployeeContractById(reportForm.getEmployeeContractId());

        if (ec == null) {
            request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator.");
            return false;
        }

        // get suborders related to employee AND selected customer order...
        long customerorderId = reportForm.getTrOrderId();
        request.getSession().setAttribute("suborders", suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderId(ec.getId(), customerorderId, reportForm.getShowOnlyValid()));

        return true;
    }

    public void adjustSuborderSignChanged(HttpSession session, AddDailyReportForm reportForm) {
        Suborder so = suborderDAO.getSuborderById(reportForm.getSuborderSignId());
        assignCurrentSuborderIdWithOvertimeCompensationAndTrainingFlag(session, so, reportForm);
    }

    public void adjustSuborderDescriptionChanged(HttpSession session, AddDailyReportForm reportForm) {
        Suborder so = suborderDAO.getSuborderById(reportForm.getSuborderDescriptionId());
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
                    !Objects.equals(session.getAttribute("overtimeCompensation"), GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
                session.setAttribute("overtimeCompensation", GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION);
            }
        } else {
            session.removeAttribute("overtimeCompensation");
        }

        // if selected Suborder has a default-flag for project based training, set training in the form to true, so that the training-box in the jsp is checked
        if (so.getTrainingFlag()) {
            reportForm.setTraining(true);
        }
    }
}
