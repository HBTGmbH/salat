package org.tb.order.viewhelper;

import static org.tb.common.util.DateUtils.parse;

import java.time.LocalDate;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tb.dailyreport.action.AddDailyReportForm;
import org.tb.dailyreport.action.ShowDailyReportForm;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.SuborderDAO;

/**
 * Helper class for suborder handling which does not directly deal with persistence
 *
 * @author oda
 */
@Component
@Slf4j
@RequiredArgsConstructor
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
        LocalDate date = parse(dateString);

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
            assignCurrentSuborderIdAndTrainingFlag(request.getSession(), so, reportForm);
        } else if (!theSuborders.isEmpty()) {
            Suborder suborder = suborderDAO.getSuborderById(reportForm.getSuborderSignId());
            if (suborder == null || !theSuborders.contains(suborder)) {
                suborder = theSuborders.getFirst();
            }
            assignCurrentSuborderIdAndTrainingFlag(request.getSession(), suborder, reportForm);
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
        assignCurrentSuborderIdAndTrainingFlag(session, so, reportForm);
    }

    private void assignCurrentSuborderIdAndTrainingFlag(HttpSession session, Suborder so, AddDailyReportForm reportForm) {
        session.setAttribute("currentSuborderId", so.getId());

        // if selected Suborder has a default-flag for project based training, set training in the form to true, so that the training-box in the jsp is checked
        if (so.getTrainingFlag()) {
            reportForm.setTraining(true);
        }
    }
}
