package org.tb.order;

import static org.tb.common.util.DateUtils.parse;
import static org.tb.common.util.DateUtils.validateDate;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tb.common.GlobalConstants;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.action.AddDailyReportForm;
import org.tb.dailyreport.action.ShowDailyReportForm;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;

/**
 * Helper class for customer order handling which does not directly deal with persistence
 *
 * @author oda
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CustomerorderHelper {

    private final CustomerorderDAO customerorderDAO;
    private final EmployeecontractDAO employeecontractDAO;
    private final SuborderDAO suborderDAO;

    /**
     * refreshes customer order list after change of employee in the 'add timereport' view
     */
    public boolean refreshOrders(HttpServletRequest request, AddDailyReportForm reportForm) {

        String dateString = reportForm.getReferenceday();
        LocalDate date;
        if(validateDate(dateString)) {
            date = DateUtils.parse(dateString);
        } else {
            resetFormValues(reportForm, request);
            return false;
        }

        Employeecontract ec = employeecontractDAO.getEmployeeContractById(reportForm.getEmployeeContractId());
        if (ec != null) {
            Employeecontract matchingTimeEC = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(ec.getEmployee().getId(), date);
            if (matchingTimeEC != null) {
                ec = matchingTimeEC;
            }
        } else {
            // TODO request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator."); //TODO: MessageResources
            resetFormValues(reportForm, request);
            return false;
        }

        // get orders related to employee
        List<Customerorder> orders = customerorderDAO.getCustomerordersWithValidEmployeeOrders(ec.getId(), date);
        if (orders.isEmpty()) {
            // TODO check error messages - request.setAttribute("errorMessage", "No orders found for employee - please call system administrator."); //TODO: MessageResources
            resetFormValues(reportForm, request);
            return false;
        }

        Customerorder customerorder = customerorderDAO.getCustomerorderById(reportForm.getOrderId());
        long suborderId = -1;
        List<Suborder> theSuborders;
        if (customerorder != null && orders.contains(customerorder)) {
            theSuborders = suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(ec.getId(), customerorder.getId(), date);
            Suborder suborder = suborderDAO.getSuborderById(reportForm.getSuborderSignId());
            if (suborder != null && theSuborders.contains(suborder)) {
                suborderId = suborder.getId();
            } else if(!theSuborders.isEmpty()) {
                suborderId = theSuborders.get(0).getId();
            }
        } else {
            customerorder = orders.get(0);
            theSuborders = suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(ec.getId(), customerorder.getId(), date);
            if (!theSuborders.isEmpty()) {
                suborderId = theSuborders.get(0).getId();
            }
        }

        // set form entries
        reportForm.setOrderId(customerorder.getId());
        reportForm.setSuborderSignId(suborderId);
        reportForm.setSuborderDescriptionId(suborderId);

        request.getSession().setAttribute("orders", orders);
        request.getSession().setAttribute("currentSuborderId", suborderId);
        request.getSession().setAttribute("suborders", theSuborders);

        request.getSession().setAttribute("currentEmployee", ec.getEmployee().getName());
        request.getSession().setAttribute("currentEmployeeId", ec.getEmployee().getId());
        request.getSession().setAttribute("currentEmployeeContract", ec);

        return true;
    }

    private void resetFormValues(AddDailyReportForm reportForm, HttpServletRequest request) {
        reportForm.setOrderId(-1);
        reportForm.setSuborderSignId(-1);
        reportForm.setSuborderDescriptionId(-1);
        request.getSession().setAttribute("orders", Collections.emptyList());
        request.getSession().setAttribute("currentSuborderId", -1);
        request.getSession().setAttribute("suborders", Collections.emptyList());
    }

    /**
     * refreshes customer order list after change of employee in the 'show timereport' views
     */
    public boolean refreshOrders(HttpServletRequest request, ShowDailyReportForm reportForm) {

        Employeecontract ec = employeecontractDAO.getEmployeeContractById(reportForm.getEmployeeContractId());

        if (ec == null) {
            request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator."); //TODO: MessageResources
            return false;
        }

        request.getSession().setAttribute("currentEmployee", ec.getEmployee().getName());
        request.getSession().setAttribute("currentEmployeeId", ec.getEmployee().getId());
        request.getSession().setAttribute("currentEmployeeContract", ec);


        // get orders related to employee
        List<Customerorder> orders = customerorderDAO.getCustomerordersByEmployeeContractId(ec.getId());
        request.getSession().setAttribute("orders", orders);

        if ((orders == null) || (orders.size() <= 0)) {
            request.setAttribute("errorMessage", "No orders found for employee - please call system administrator."); //TODO: MessageResources
            return false;
        }
        // get suborders related to employee AND selected customer order...
        long customerorderId = orders.get(0).getId();
        request.getSession().setAttribute("suborders", suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderId(ec.getId(), customerorderId, reportForm.getShowOnlyValid()));

        return true;
    }

    public boolean isOrderStandard(Customerorder order) {
        return order != null &&
                (order.getSign().equalsIgnoreCase(GlobalConstants.CUSTOMERORDER_SIGN_VACATION) ||
                        order.getSign().equalsIgnoreCase(GlobalConstants.CUSTOMERORDER_SIGN_EXTRA_VACATION) ||
                        order.getSign().equalsIgnoreCase(GlobalConstants.CUSTOMERORDER_SIGN_ILL) ||
                        order.getSign().equalsIgnoreCase(GlobalConstants.CUSTOMERORDER_SIGN_REMAINING_VACATION));
    }

}
