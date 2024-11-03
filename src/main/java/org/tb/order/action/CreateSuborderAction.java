package org.tb.order.action;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.GlobalConstants;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.common.util.DateUtils;
import org.tb.employee.domain.Employee;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.OrderType;
import org.tb.order.persistence.CustomerorderDAO;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.SuborderDAO;

/**
 * action class for creating a new suborder
 *
 * @author oda, th
 */
@Component
@RequiredArgsConstructor
public class CreateSuborderAction extends LoginRequiredAction<AddSuborderForm> {

    private final CustomerorderDAO customerorderDAO;
    private final SuborderDAO suborderDAO;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddSuborderForm suborderForm, HttpServletRequest request, HttpServletResponse response) {

        // remove list with timereports out of range
        request.getSession().removeAttribute("timereportsOutOfRange");

        // get login employee
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");

        // get lists of existing customerorders and suborders
        List<Customerorder> customerorders;
        if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_BL) ||
                loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_PV) ||
                loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
            customerorders = customerorderDAO.getVisibleCustomerorders();
        } else {
            customerorders = customerorderDAO.getVisibleCustomerOrdersByResponsibleEmployeeId(loginEmployee.getId());
        }


        List<Suborder> suborders = suborderDAO.getSuborders(false);

        if ((customerorders == null) || (customerorders.size() <= 0)) {
            request.setAttribute("errorMessage", "No customer orders found - please call system administrator.");
            return mapping.findForward("error");
        }

        // set relevant attributes
        request.getSession().setAttribute("customerorders", customerorders);
        request.getSession().setAttribute("suborders", suborders);
        request.getSession().setAttribute("invoice", GlobalConstants.INVOICE_YES);

        // use customer order from filter
        Long customerOrderId = (Long) request.getSession().getAttribute("suborderCustomerOrderId");
        if (customerOrderId != null) {
            suborderForm.setCustomerorderId(customerOrderId);
        }

        // use last customer order als default if present
        if (request.getSession().getAttribute("lastCoId") != null) {
            long id = (Long) request.getSession().getAttribute("lastCoId");
            request.getSession().setAttribute("currentOrderId", id);
            Customerorder customerorder = customerorderDAO.getCustomerorderById(id);
            request.getSession().setAttribute("currentOrder", customerorder);
            suborderForm.setCustomerorderId(id);
        }

        // reset/init form entries
        suborderForm.reset(mapping, request);

        if (customerorders.size() > 0) {
            if (request.getSession().getAttribute("lastCoId") == null) {
                request.getSession().setAttribute("currentOrderId", customerorders.getFirst().getId());
                request.getSession().setAttribute("currentOrder", customerorders.getFirst());
            }

            Customerorder customerorder;

            if (customerOrderId != null && customerorderDAO.getCustomerorderById(customerOrderId) != null) {
                customerorder = customerorderDAO.getCustomerorderById(customerOrderId);
            } else {
                customerorder = customerorders.getFirst();
            }
            request.getSession().setAttribute("currentOrderId", customerorder.getId());
            request.getSession().setAttribute("currentOrder", customerorder);
            request.getSession().setAttribute("parentDescriptionAndSign", customerorder.getSignAndDescription());
            request.getSession().setAttribute("suborderParent", customerorder);
            request.getSession().setAttribute("currentSuborderID", null);

            suborderForm.setParentDescriptionAndSign(customerorder.getSignAndDescription());
            suborderForm.setParentId(customerorder.getId());

            suborderForm.setValidFrom(DateUtils.format(customerorder.getFromDate()));

            if (customerorder.getUntilDate() != null) {
                suborderForm.setValidUntil(DateUtils.format(customerorder.getUntilDate()));
            } else {
                suborderForm.setValidUntil("");
            }

            suborderForm.setHide(customerorder.getHide());
        }

        // make sure, no soId still exists in session
        request.getSession().removeAttribute("soId");

        request.getSession().setAttribute("orderTypes", OrderType.values());

        // forward to form jsp
        return mapping.findForward("success");
    }

}
