package org.tb.customer;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.stereotype.Component;
import org.tb.common.GlobalConstants;
import org.tb.common.struts.LoginRequiredAction;

/**
 * action class for storing a customer permanently
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class StoreCustomerAction extends LoginRequiredAction<AddCustomerForm> {

    private final CustomerDAO customerDAO;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddCustomerForm cuForm, HttpServletRequest request, HttpServletResponse response) {
        if ((request.getParameter("task") != null) &&
                (request.getParameter("task").equals("save")) ||
                (request.getParameter("cuId") != null)) {

            // 'main' task - prepare everything to store the customer.
            // I.e., copy properties from the form into the customer before saving.
            long cuId;
            Customer cu;
            if (request.getSession().getAttribute("cuId") != null) {
                // edited customer
                cuId = Long.parseLong(request.getSession().getAttribute("cuId").toString());
                cu = customerDAO.getCustomerById(cuId);
            } else {
                // new customer
                cu = new Customer();
            }

            ActionMessages errorMessages = validateFormData(request, cuForm);
            if (errorMessages.size() > 0) {
                return mapping.getInputForward();
            }

            cu.setName(cuForm.getName());
            cu.setShortname(cuForm.getShortname());
            cu.setAddress(cuForm.getAddress());

            customerDAO.save(cu);

//				request.getSession().setAttribute("customers", customerDAO.getCustomers());
            request.getSession().removeAttribute("cuId");

            boolean addMoreCustomers = Boolean.parseBoolean(request.getParameter("continue"));
            if (!addMoreCustomers) {
                String filter = null;

                if (request.getSession().getAttribute("customerFilter") != null) {
                    filter = (String) request.getSession().getAttribute("customerFilter");
                }

                request.getSession().setAttribute("customers", customerDAO.getCustomersByFilter(filter));

                return mapping.findForward("success");
            } else {
                // reset form and show add-page
                cuForm.reset(mapping, request);
                return mapping.findForward("reset");
            }
        }
        if ((request.getParameter("task") != null) &&
                (request.getParameter("task").equals("back"))) {
            // go back
            request.getSession().removeAttribute("cuId");
            cuForm.reset(mapping, request);
            return mapping.findForward("cancel");
        }
        if ((request.getParameter("task") != null) &&
                (request.getParameter("task").equals("reset"))) {
            // reset form
            doResetActions(mapping, request, cuForm);
            return mapping.getInputForward();
        }

        return mapping.findForward("error");

    }

    /**
     * resets the 'add customer' form to default values
     */
    private void doResetActions(ActionMapping mapping, HttpServletRequest request, AddCustomerForm cuForm) {
        cuForm.reset(mapping, request);
    }

    /**
     * validates the form data (syntax and logic)
     */
    private ActionMessages validateFormData(HttpServletRequest request, AddCustomerForm cuForm) {

        ActionMessages errors = getErrors(request);
        if (errors == null) errors = new ActionMessages();

        // for a new customer, check if name already exists
        if (request.getSession().getAttribute("cuId") == null) {
            List<Customer> allCustomers = customerDAO.getCustomers();
            for (Customer cu : allCustomers) {
                if (cu.getName().equalsIgnoreCase(cuForm.getName())) {
                    errors.add("name", new ActionMessage("form.customer.error.name.alreadyexists"));
                    break;
                }
            }
        }

        // check length of text fields and if they are filled
        if (cuForm.getName().length() > GlobalConstants.CUSTOMERNAME_MAX_LENGTH) {
            errors.add("name", new ActionMessage("form.customer.error.name.toolong"));
        }
        if (cuForm.getShortname().length() > GlobalConstants.CUSTOMERSHORTNAME_MAX_LENGTH) {
            errors.add("shortname", new ActionMessage("form.customer.error.shortname.toolong"));
        }
        if (cuForm.getName().length() <= 0) {
            errors.add("name", new ActionMessage("form.customer.error.name.required"));
        }
        if (cuForm.getAddress().length() > GlobalConstants.CUSTOMERADDRESS_MAX_LENGTH) {
            errors.add("address", new ActionMessage("form.customer.error.address.toolong"));
        }
        if (cuForm.getAddress().length() <= 0) {
            errors.add("address", new ActionMessage("form.customer.error.address.required"));
        }

        saveErrors(request, errors);

        return errors;
    }
}
