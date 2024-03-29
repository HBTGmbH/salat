package org.tb.order.action;

import java.util.LinkedList;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.order.viewhelper.CustomerOrderViewDecorator;
import org.tb.order.domain.Customerorder;
import org.tb.order.persistence.CustomerorderDAO;

/**
 * action class for deleting a customer order
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class DeleteCustomerorderAction extends LoginRequiredAction<ShowCustomerorderForm> {

    private final CustomerorderDAO customerorderDAO;
    private final TimereportDAO timereportDAO;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ShowCustomerorderForm orderForm, HttpServletRequest request, HttpServletResponse response) {

        if (GenericValidator.isBlankOrNull(request.getParameter("coId")) ||
                !GenericValidator.isLong(request.getParameter("coId"))) {
            return mapping.getInputForward();
        }

        ActionMessages errors = new ActionMessages();
        long coId = Long.parseLong(request.getParameter("coId"));
        Customerorder co = customerorderDAO.getCustomerorderById(coId);
        if (co == null) {
            return mapping.getInputForward();
        }

        boolean deleted = customerorderDAO.deleteCustomerorderById(coId);

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
            List<Customerorder> customerOrders = customerorderDAO.getCustomerordersByFilters(show, filter, customerId);
            List<CustomerOrderViewDecorator> decorators = new LinkedList<CustomerOrderViewDecorator>();
            for (Customerorder customerorder : customerOrders) {
                CustomerOrderViewDecorator decorator = new CustomerOrderViewDecorator(timereportDAO, customerorder);
                decorators.add(decorator);
            }
            request.getSession().setAttribute("customerorders", decorators);
        } else {
            request.getSession().setAttribute("customerorders", customerorderDAO.getCustomerordersByFilters(show, filter, customerId));
        }

        // back to customer order display jsp
        return mapping.getInputForward();
    }
}
