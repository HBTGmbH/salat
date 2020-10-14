package org.tb.web.action.admin;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Customer;
import org.tb.persistence.CustomerDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddCustomerForm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * action class for editing a customer
 *
 * @author oda
 */
public class EditCustomerAction extends LoginRequiredAction {

    private CustomerDAO customerDAO;

    public void setCustomerDAO(CustomerDAO customerDAO) {
        this.customerDAO = customerDAO;
    }


    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {

        AddCustomerForm cuForm = (AddCustomerForm) form;
        long cuId = Long.parseLong(request.getParameter("cuId"));
        Customer cu = customerDAO.getCustomerById(cuId);
        request.getSession().setAttribute("cuId", cu.getId());

        // fill the form with properties of cústomer to be edited
        setFormEntries(mapping, request, cuForm, cu);

        // forward to customer add/edit form
        return mapping.findForward("success");
    }

    /**
     * fills customer form with properties of given cústomer
     *
     * @param mapping
     * @param request
     * @param customerForm
     * @param cu           - the customer
     */
    private void setFormEntries(ActionMapping mapping, HttpServletRequest request,
                                AddCustomerForm customerForm, Customer cu) {
        customerForm.setName(cu.getName());
        customerForm.setShortname(cu.getShortname());
        customerForm.setAddress(cu.getAddress());
    }

}
