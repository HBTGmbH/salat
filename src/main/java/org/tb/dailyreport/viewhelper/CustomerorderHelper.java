package org.tb.dailyreport.viewhelper;

import static org.tb.common.util.DateUtils.validateDate;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.action.AddDailyReportForm;
import org.tb.dailyreport.action.ShowDailyReportForm;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.OrderType;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

/**
 * Helper class for customer order handling which does not directly deal with persistence
 *
 * @author oda
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CustomerorderHelper {

    private final CustomerorderService customerorderService;
    private final EmployeecontractService employeecontractService;
    private final SuborderService suborderService;

    /**
     * refreshes customer order list after change of employee in the 'add timereport' view
     */
    public void refreshOrders(HttpServletRequest request, AddDailyReportForm reportForm) {

        String dateString = reportForm.getReferenceday();
        LocalDate date;
        if(validateDate(dateString)) {
            date = DateUtils.parse(dateString);
        } else {
            resetFormValues(reportForm, request);
            return;
        }

        Employeecontract ec = employeecontractService.getEmployeecontractById(reportForm.getEmployeeContractId());
        if (ec != null) {
            Employeecontract matchingTimeEC = employeecontractService.getEmployeeContractValidAt(ec.getEmployee().getId(), date);
            if (matchingTimeEC != null) {
                ec = matchingTimeEC;
            }
        } else {
            // TODO request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator."); //TODO: MessageResources
            resetFormValues(reportForm, request);
            return;
        }

        // get orders related to employee
        List<Customerorder> orders = customerorderService.getCustomerordersWithValidEmployeeOrders(ec.getId(), date);
        if (orders.isEmpty()) {
            // TODO check error messages - request.setAttribute("errorMessage", "No orders found for employee - please call system administrator."); //TODO: MessageResources
            resetFormValues(reportForm, request);
            return;
        }

        Customerorder customerorder = customerorderService.getCustomerorderById(reportForm.getOrderId());
        long suborderId = -1;
        List<Suborder> theSuborders;
        if (customerorder != null && orders.contains(customerorder)) {
            theSuborders = suborderService.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(ec.getId(), customerorder.getId(), date);
            Suborder suborder = suborderService.getSuborderById(reportForm.getSuborderSignId());
            if (suborder != null && theSuborders.contains(suborder)) {
                suborderId = suborder.getId();
            } else if(!theSuborders.isEmpty()) {
                suborderId = theSuborders.getFirst().getId();
            }
        } else {
            customerorder = orders.getFirst();
            theSuborders = suborderService.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(ec.getId(), customerorder.getId(), date);
            if (!theSuborders.isEmpty()) {
                suborderId = theSuborders.getFirst().getId();
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

        Employeecontract ec = employeecontractService.getEmployeecontractById(reportForm.getEmployeeContractId());

        if (ec == null) {
            request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator."); //TODO: MessageResources
            return false;
        }

        request.getSession().setAttribute("currentEmployee", ec.getEmployee().getName());
        request.getSession().setAttribute("currentEmployeeId", ec.getEmployee().getId());
        request.getSession().setAttribute("currentEmployeeContract", ec);


        // get orders related to employee
        List<Customerorder> orders = customerorderService.getCustomerordersByEmployeeContractId(ec.getId());
        request.getSession().setAttribute("orders", orders);

        if ((orders == null) || (orders.size() <= 0)) {
            request.setAttribute("errorMessage", "No orders found for employee - please call system administrator."); //TODO: MessageResources
            return false;
        }
        // get suborders related to employee AND selected customer order...
        long customerorderId = orders.getFirst().getId();
        request.getSession().setAttribute("suborders", suborderService.getSubordersByEmployeeContractIdAndCustomerorderId(ec.getId(), customerorderId, reportForm.getShowOnlyValid()));

        return true;
    }

    public boolean isOrderStandard(Customerorder order) {
        return order.getOrderType() == OrderType.KRANK_URLAUB_ABWESEND;
    }

}
