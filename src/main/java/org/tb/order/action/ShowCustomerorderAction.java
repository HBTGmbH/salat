package org.tb.order.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.customer.domain.Customer;
import org.tb.customer.service.CustomerService;
import org.tb.order.domain.Customerorder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.viewhelper.CustomerOrderViewDecorator;

/**
 * action class for showing all customer orders
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class ShowCustomerorderAction extends LoginRequiredAction<ShowCustomerorderForm> {

    private final CustomerorderService customerorderService;
    private final CustomerService customerService;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping,
        ShowCustomerorderForm orderForm, HttpServletRequest request,
        HttpServletResponse response) {

        List<Customer> customers = customerService.getCustomersOrderedByShortName();
        request.getSession().setAttribute("customers", customers);

        String filter = null;
        Boolean show = null;
        Long customerId = null;

        if ((request.getParameter("task") != null) &&
                (request.getParameter("task").equals("refresh"))) {
            filter = orderForm.getFilter();
            request.getSession().setAttribute("customerorderFilter", filter);

            show = orderForm.getShow();
            request.getSession().setAttribute("customerorderShow", show);

            customerId = orderForm.getCustomerId();
            request.getSession().setAttribute("customerorderCustomerId", customerId);
        } else {
            if (request.getSession().getAttribute("customerorderFilter") != null) {
                filter = (String) request.getSession().getAttribute("customerorderFilter");
                orderForm.setFilter(filter);
            }
            if (request.getSession().getAttribute("customerorderShow") != null) {
                show = (Boolean) request.getSession().getAttribute("customerorderShow");
                orderForm.setShow(show);
            }
            if (request.getSession().getAttribute("customerorderCustomerId") != null) {
                customerId = (Long) request.getSession().getAttribute("customerorderCustomerId");
                orderForm.setCustomerId(customerId);
            }
        }

        boolean showActualHours = orderForm.getShowActualHours();
        request.getSession().setAttribute("showActualHours", showActualHours);

        if (showActualHours) {
            /* show actual hours */
            List<Customerorder> customerOrders = customerorderService.getCustomerordersByFilters(show, filter, customerId);
            List<CustomerOrderViewDecorator> decorators = new LinkedList<>();
            for (Customerorder customerorder : customerOrders) {
                CustomerOrderViewDecorator decorator = new CustomerOrderViewDecorator(customerorderService, customerorder);
                decorators.add(decorator);
            }
            request.getSession().setAttribute("customerorders", decorators);
        } else {
            request.getSession().setAttribute("customerorders", customerorderService.getCustomerordersByFilters(show, filter, customerId));

        }


        if (request.getParameter("task") != null) {
            if (request.getParameter("task").equalsIgnoreCase("back")) {
                // back to main menu
                return mapping.findForward("backtomenu");
            } else {
                // forward to show customer orders jsp
                return mapping.findForward("success");
            }
        } else {

            // forward to show customer orders jsp
            return mapping.findForward("success");
        }
    }

}
