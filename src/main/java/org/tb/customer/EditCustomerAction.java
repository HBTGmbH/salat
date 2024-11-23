package org.tb.customer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;

/**
 * action class for editing a customer
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class EditCustomerAction extends LoginRequiredAction<AddCustomerForm> {

    private final CustomerService customerService;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddCustomerForm cuForm, HttpServletRequest request, HttpServletResponse response) {
        long cuId = Long.parseLong(request.getParameter("cuId"));
        CustomerDTO cu = customerService.get(cuId);
        request.getSession().setAttribute("cuId", cu.getId());

        // fill the form with properties of customer to be edited
        setFormEntries(cuForm, cu);

        // forward to customer add/edit form
        return mapping.findForward("success");
    }

    /**
     * fills customer form with properties of given customer
     */
    private void setFormEntries(AddCustomerForm customerForm, CustomerDTO cu) {
        customerForm.setName(cu.getName());
        customerForm.setShortname(cu.getShortName());
        customerForm.setAddress(cu.getAddress());
    }

}
