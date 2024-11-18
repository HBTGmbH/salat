package org.tb.customer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;

/**
 * action class for showing all customers
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class ShowCustomerAction extends LoginRequiredAction<ShowCustomerForm> {

    private final CustomerService customerService;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping,
        ShowCustomerForm customerForm, HttpServletRequest request,
        HttpServletResponse response) {

        String filter = null;

        if ((request.getParameter("task") != null) &&
                (request.getParameter("task").equals("refresh"))) {
            filter = customerForm.getFilter();

            request.getSession().setAttribute("customerFilter", filter);

        } else {
            if (request.getSession().getAttribute("customerFilter") != null) {
                filter = (String) request.getSession().getAttribute("customerFilter");
                customerForm.setFilter(filter);
            }
        }

        request.getSession().setAttribute("customers", customerService.list(filter));


        if (request.getParameter("task") != null) {
            if (request.getParameter("task").equalsIgnoreCase("back")) {
                // back to main menu
                return mapping.findForward("backtomenu");
            } else {
                // forward to show customers jsp
                return mapping.findForward("success");
            }
        } else {
            // forward to show customers jsp
            return mapping.findForward("success");
        }
    }

}
