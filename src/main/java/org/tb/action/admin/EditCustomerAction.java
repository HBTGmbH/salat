package org.tb.action.admin;

import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.action.LoginRequiredAction;
import org.tb.bdom.Customer;
import org.tb.persistence.CustomerDAO;
import org.tb.form.AddCustomerForm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * action class for editing a customer
 *
 * @author oda
 */
public class EditCustomerAction extends LoginRequiredAction<AddCustomerForm> {

    private CustomerDAO customerDAO;

    public void setCustomerDAO(CustomerDAO customerDAO) {
        this.customerDAO = customerDAO;
    }

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddCustomerForm cuForm, HttpServletRequest request, HttpServletResponse response) {
        long cuId = Long.parseLong(request.getParameter("cuId"));
        Customer cu = customerDAO.getCustomerById(cuId);
        request.getSession().setAttribute("cuId", cu.getId());

        // fill the form with properties of customer to be edited
        setFormEntries(cuForm, cu);

        // forward to customer add/edit form
        return mapping.findForward("success");
    }

    /**
     * fills customer form with properties of given customer
     */
    private void setFormEntries(AddCustomerForm customerForm, Customer cu) {
        customerForm.setName(cu.getName());
        customerForm.setShortname(cu.getShortname());
        customerForm.setAddress(cu.getAddress());
    }

}
