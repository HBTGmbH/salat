package org.tb.order.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.dailyreport.service.TimereportService;
import org.tb.order.domain.Customerorder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.viewhelper.CustomerOrderViewDecorator;

/**
 * action class for deleting a customer order
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class DeleteCustomerorderAction extends LoginRequiredAction<ShowCustomerorderForm> {

    private final CustomerorderService customerorderService;
    private final TimereportService timereportService;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ShowCustomerorderForm orderForm, HttpServletRequest request, HttpServletResponse response) {

        if (GenericValidator.isBlankOrNull(request.getParameter("coId")) ||
                !GenericValidator.isLong(request.getParameter("coId"))) {
            return mapping.getInputForward();
        }

        ActionMessages errors = new ActionMessages();
        long coId = Long.parseLong(request.getParameter("coId"));
        Customerorder co = customerorderService.getCustomerorderById(coId);
        if (co == null) {
            return mapping.getInputForward();
        }

        boolean deleted = customerorderService.deleteCustomerorderById(coId);

        Long coID = (Long) request.getSession().getAttribute("currentOrderId");

        //fix for accessing deleted Order in EmployeeOrderAction
        if (deleted && coID != null && coID.equals(coId)) {
            request.getSession().setAttribute("currentOrderId", -2L);
        }

        if (!deleted) {
            errors.add(null, new ActionMessage("form.customerorder.error.hassuborders"));
        }
        saveErrors(request, errors);
        String filter = null;
        Boolean show = null;
        Long customerId = null;
        if (request.getSession().getAttribute("customerorderFilter") != null) {
            filter = (String) request.getSession().getAttribute("customerorderFilter");
        }
        if (request.getSession().getAttribute("customerorderShow") != null) {
            show = (Boolean) request.getSession().getAttribute("customerorderShow");
        }
        if (request.getSession().getAttribute("customerorderCustomerId") != null) {
            customerId = (Long) request.getSession().getAttribute("customerorderCustomerId");
        }

        orderForm.setFilter(filter);
        orderForm.setShow(show);
        orderForm.setCustomerId(customerId);

        boolean showActualHours = (Boolean) request.getSession().getAttribute("showActualHours");
        orderForm.setShowActualHours(showActualHours);
        if (showActualHours) {
            /* show actual hours */
            List<Customerorder> customerOrders = customerorderService.getCustomerordersByFilters(show, filter, customerId);
            List<CustomerOrderViewDecorator> decorators = new LinkedList<CustomerOrderViewDecorator>();
            for (Customerorder customerorder : customerOrders) {
                CustomerOrderViewDecorator decorator = new CustomerOrderViewDecorator(timereportService, customerorder);
                decorators.add(decorator);
            }
            request.getSession().setAttribute("customerorders", decorators);
        } else {
            request.getSession().setAttribute("customerorders", customerorderService.getCustomerordersByFilters(show, filter, customerId));
        }

        // back to customer order display jsp
        return mapping.getInputForward();
    }
}
