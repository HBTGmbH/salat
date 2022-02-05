package org.tb.helper;

import java.text.ParseException;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Suborder;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.form.AddDailyReportForm;
import org.tb.form.ShowDailyReportForm;

import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Helper class for customer order handling which does not directly deal with persistence
 *
 * @author oda
 */
@Component
@Slf4j
@RequiredArgsConstructor(onConstructor_ = { @Autowired})
public class CustomerorderHelper {

    private final CustomerorderDAO customerorderDAO;
    private final EmployeecontractDAO employeecontractDAO;
    private final SuborderDAO suborderDAO;

    /**
     * refreshes customer order list after change of employee in the 'add timereport' view
     */
    public boolean refreshOrders(HttpServletRequest request, AddDailyReportForm reportForm) {

        // initialize with empty values (in case of any error we return an empty result)
        reportForm.setOrderId(-1);
        reportForm.setSuborderSignId(-1);
        reportForm.setSuborderDescriptionId(-1);
        request.getSession().setAttribute("orders", Collections.emptyList());
        request.getSession().setAttribute("currentSuborderId", -1);
        request.getSession().setAttribute("suborders", Collections.emptyList());

        String dateString = reportForm.getReferenceday();
        SimpleDateFormat dateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
        Date date;
        try {
            date = dateFormat.parse(dateString);
        } catch (ParseException e) {
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
            return false;
        }

        // get orders related to employee
        List<Customerorder> orders = customerorderDAO.getCustomerordersWithValidEmployeeOrders(ec.getId(), date);
        if (orders.isEmpty()) {
            // TODO check error messages - request.setAttribute("errorMessage", "No orders found for employee - please call system administrator."); //TODO: MessageResources
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
