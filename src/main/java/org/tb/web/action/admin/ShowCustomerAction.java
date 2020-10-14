package org.tb.web.action.admin;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.persistence.CustomerDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.ShowCustomerForm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * action class for showing all customers
 *
 * @author oda
 */
public class ShowCustomerAction extends LoginRequiredAction {


    private CustomerDAO customerDAO;

    public void setCustomerDAO(CustomerDAO customerDAO) {
        this.customerDAO = customerDAO;
    }

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping,
                                              ActionForm form, HttpServletRequest request,
                                              HttpServletResponse response) {

        ShowCustomerForm customerForm = (ShowCustomerForm) form;

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

        request.getSession().setAttribute("customers", customerDAO.getCustomersByFilter(filter));


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
