package org.tb.customer;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;

/**
 * action class for creating a new customer
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class CreateCustomerAction extends LoginRequiredAction<AddCustomerForm> {

    private final CustomerDAO customerDAO;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddCustomerForm customerForm, HttpServletRequest request, HttpServletResponse response) {

        // get list of existing customers
        List<Customer> customers = customerDAO.getCustomers();
        request.getSession().setAttribute("customers", customers);

        // init form entries
        customerForm.setName("");
        customerForm.setShortname("");
        customerForm.setAddress("");

        // make sure, no cuId still exists in session
        request.getSession().removeAttribute("cuId");

        // forward to form jsp
        return mapping.findForward("success");
    }

}
